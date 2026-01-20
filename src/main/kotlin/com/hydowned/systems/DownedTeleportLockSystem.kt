package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Vector3f
import com.hypixel.hytale.protocol.ComponentUpdate
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.protocol.Direction
import com.hypixel.hytale.protocol.ModelTransform
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates
import com.hypixel.hytale.protocol.packets.player.ClientTeleport
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.PositionUtil
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hypixel.hytale.protocol.EntityUpdate

/**
 * Forces client position synchronization and sleeping state for downed players.
 *
 * This system runs every 10 ticks (~0.5s) and performs two critical tasks:
 *
 * 1. POSITION LOCKING (preserves rotation):
 *    - Sends ClientTeleport packet with current server rotation (echoing it back)
 *    - This locks ONLY the position to the downed location
 *    - Uses PendingTeleport to make server ignore client position updates until ack
 *    - Rotation flow: Client sends ClientMovement → Interceptor allows rotation through →
 *      Server updates rotation → We read it and send it back → No rotation change occurs
 *    - Client fully controls their own rotation without snapping
 *
 * 2. SELF-VIEW ANIMATION FIX:
 *    - Sends EntityUpdates packet to the player about THEMSELVES with sleeping=true
 *    - Normally players don't receive EntityUpdates about their own entity (optimization)
 *    - This causes downed player to see themselves as idle while others see them laying down
 *    - We manually send the update to ensure the downed player sees themselves laying down
 */
class DownedTeleportLockSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private var tickCounter = 0

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        TransformComponent.getComponentType(),
        PlayerRef.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        tickCounter++

        // Force teleport every 10 ticks (~0.5s)
        if (tickCounter % 10 != 0) return

        val ref = archetypeChunk.getReferenceTo(index)
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return
        val transformComponent = archetypeChunk.getComponent(index, TransformComponent.getComponentType())
            ?: return
        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return

        // Get the downed location (where player was when they got downed)
        val downedLocation = downedComponent.downedLocation ?: return

        // CRITICAL: Ensure PendingTeleport component exists
        val pendingTeleport = commandBuffer.ensureAndGetComponent(ref, PendingTeleport.getComponentType())

        // Set server-side position to downed location (ONLY position, not rotation)
        transformComponent.teleportPosition(downedLocation)

        // Read CURRENT rotation from server (updated by ClientMovement packets we allowed through)
        // We send this back unchanged so the teleport doesn't override the client's rotation
        val currentRotation = transformComponent.getRotation()

        // CRITICAL: Create Teleport object and queue it in PendingTeleport
        // This makes the server IGNORE all client POSITION updates until acknowledged
        val teleport = Teleport.createForPlayer(downedLocation, currentRotation)
        val teleportId = pendingTeleport.queueTeleport(teleport)

        // Create and send ClientTeleport packet with CURRENT rotation
        // IMPORTANT: We send the server's current rotation back to avoid overriding client's rotation
        // The server's rotation is kept up-to-date by ClientMovement packets (interceptor allows them)
        // This teleport locks POSITION but preserves ROTATION
        val teleportPacket = ClientTeleport(
            teleportId.toByte(),
            ModelTransform(
                PositionUtil.toPositionPacket(downedLocation),
                PositionUtil.toDirectionPacket(currentRotation), // Send current rotation back unchanged
                PositionUtil.toDirectionPacket(currentRotation)  // Use same for head (body and head aligned when lying down)
            ),
            false // Don't reset velocity - might help preserve animation state
        )

        playerRefComponent.getPacketHandler().write(teleportPacket)

        // CRITICAL: Send EntityUpdates to the player about THEMSELVES to force sleeping state
        // Players normally don't receive EntityUpdates about their own entity (optimization)
        // But we need the downed player to see themselves laying down, not idle
        sendSelfMovementStateUpdate(ref, commandBuffer, playerRefComponent)

        if (tickCounter % 100 == 0) {
            println("[HyDowned] Force-teleported client to downed position (ID: $teleportId, pending: ${!pendingTeleport.isEmpty})")
        }
    }

    /**
     * Sends an EntityUpdates packet to the player about themselves with sleeping=true.
     *
     * Players don't normally receive EntityUpdates about their own entity (the game skips self-updates).
     * This causes the downed player to see themselves as idle while others see them laying down.
     * We manually send the update to fix this.
     */
    private fun sendSelfMovementStateUpdate(
        ref: com.hypixel.hytale.component.Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        playerRefComponent: PlayerRef
    ) {
        // Get player's network ID
        val networkIdComponent = commandBuffer.getComponent(ref, NetworkId.getComponentType())
            ?: return
        val networkId = networkIdComponent.id

        // Create MovementStates with sleeping=true and all other states false
        val movementStates = MovementStates()
        movementStates.sleeping = true
        movementStates.idle = false
        movementStates.horizontalIdle = false
        movementStates.walking = false
        movementStates.running = false
        movementStates.sprinting = false
        movementStates.jumping = false
        movementStates.falling = false
        movementStates.crouching = false
        movementStates.forcedCrouching = false
        movementStates.climbing = false
        movementStates.flying = false
        movementStates.inFluid = false
        movementStates.swimming = false
        movementStates.swimJumping = false
        movementStates.mantling = false
        movementStates.sliding = false
        movementStates.mounting = false
        movementStates.rolling = false
        movementStates.sitting = false
        movementStates.gliding = false
        movementStates.onGround = true

        // Create ComponentUpdate for MovementStates
        val componentUpdate = ComponentUpdate()
        componentUpdate.type = ComponentUpdateType.MovementStates
        componentUpdate.movementStates = movementStates

        // Create EntityUpdate for this player
        val entityUpdate = EntityUpdate()
        entityUpdate.networkId = networkId
        entityUpdate.updates = arrayOf(componentUpdate)

        // Create EntityUpdates packet
        val entityUpdatesPacket = EntityUpdates(
            null, // no removed entities
            arrayOf(entityUpdate)
        )

        // Send to the player (about themselves)
        playerRefComponent.packetHandler.writeNoCache(entityUpdatesPacket)
    }
}
