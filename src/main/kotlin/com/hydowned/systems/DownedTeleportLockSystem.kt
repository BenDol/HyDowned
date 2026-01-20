package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Vector3f
import com.hypixel.hytale.protocol.ModelTransform
import com.hypixel.hytale.protocol.packets.player.ClientTeleport
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.PositionUtil
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Forces client position synchronization by sending ClientTeleport packets.
 *
 * This system runs every 10 ticks (~0.5s) and sends a ClientTeleport packet
 * to the downed player, forcing their client to snap back to the downed position.
 * This overrides client-side prediction completely.
 *
 * Uses the PROPER approach from TeleportSystems.ExecuteSystem:
 * 1. Ensures PendingTeleport component exists
 * 2. Queues the teleport using PendingTeleport.queueTeleport()
 * 3. Sends ClientTeleport packet with the queued ID
 * 4. While PendingTeleport is not empty, server IGNORES all client movement
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

        // IMPORTANT: Read current rotation but DON'T lock it - allow player to look around
        val rotation = transformComponent.getRotation()
        val headRotationComponent = commandBuffer.getComponent(ref, HeadRotation.getComponentType())
        val headRotation = headRotationComponent?.getRotation() ?: rotation

        // CRITICAL: Ensure PendingTeleport component exists
        val pendingTeleport = commandBuffer.ensureAndGetComponent(ref, PendingTeleport.getComponentType())

        // Set server-side position to downed location (ONLY position, not rotation)
        transformComponent.teleportPosition(downedLocation)
        // DO NOT teleport rotation - allow player to rotate freely

        // CRITICAL: Create Teleport object and queue it in PendingTeleport
        // This makes the server IGNORE all client POSITION updates until acknowledged
        // We use current rotation so it doesn't snap, but rotation updates will still flow
        val teleport = Teleport.createForPlayer(downedLocation, rotation)
            .setHeadRotation(headRotation)
        // resetVelocity is true by default
        val teleportId = pendingTeleport.queueTeleport(teleport)

        // Create and send ClientTeleport packet with the queued ID
        // We send the current rotation to avoid snapping, but client can still update rotation
        val teleportPacket = ClientTeleport(
            teleportId.toByte(),
            ModelTransform(
                PositionUtil.toPositionPacket(downedLocation),
                PositionUtil.toDirectionPacket(rotation),
                PositionUtil.toDirectionPacket(headRotation)
            ),
            true // resetVelocity = true
        )

        playerRefComponent.getPacketHandler().write(teleportPacket)

        if (tickCounter % 100 == 0) {
            println("[HyDowned] Force-teleported client to downed position (ID: $teleportId, pending: ${!pendingTeleport.isEmpty})")
        }
    }
}
