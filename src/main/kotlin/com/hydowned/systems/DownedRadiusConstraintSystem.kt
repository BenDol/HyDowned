package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.ModelTransform
import com.hypixel.hytale.protocol.packets.player.ClientTeleport
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.PositionUtil
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * Enforces a 3 block radius constraint for downed players.
 *
 * This system runs every tick and checks if the downed player has moved more than
 * 3 blocks away from their phantom body (downed location). If they have, the player
 * is teleported back within the radius using ClientTeleport packets.
 *
 * This allows downed players to move freely and look around while still being
 * constrained to the area near their body.
 */
class DownedRadiusConstraintSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    companion object {
        private const val MAX_DISTANCE_FROM_BODY = 7.0 // blocks
        private const val TELEPORT_BACK_DISTANCE = 5.0
    }

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
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return
        val transformComponent = archetypeChunk.getComponent(index, TransformComponent.getComponentType())
            ?: return
        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return

        val downedLocation = downedComponent.downedLocation ?: return
        val currentLocation = transformComponent.position

        // Calculate horizontal distance (ignore Y to allow vertical movement)
        val dx = currentLocation.x - downedLocation.x
        val dz = currentLocation.z - downedLocation.z
        val horizontalDistance = sqrt(dx * dx + dz * dz)

        // Debug logging (uncomment to verify distance tracking)
        // println("[HyDowned] [RadiusConstraint] Distance: %.2f / %.2f blocks".format(horizontalDistance, MAX_DISTANCE_FROM_BODY))

        // Check if player has exceeded the radius
        if (horizontalDistance > MAX_DISTANCE_FROM_BODY) {
            // Calculate direction from body to player
            val angle = atan2(dz, dx)

            // Set position at TELEPORT_BACK_DISTANCE (not max) to prevent jittering
            // Teleporting to 8 blocks instead of 10 creates a buffer zone
            val newX = downedLocation.x + cos(angle) * TELEPORT_BACK_DISTANCE
            val newZ = downedLocation.z + sin(angle) * TELEPORT_BACK_DISTANCE

            // Keep current Y position (allow vertical movement)
            val constrainedLocation = downedLocation.clone()
            constrainedLocation.x = newX
            constrainedLocation.z = newZ
            constrainedLocation.y = currentLocation.y

            // Get current rotation to preserve it
            val currentRotation = transformComponent.rotation

            // Get reference to entity
            val ref = archetypeChunk.getReferenceTo(index)

            // Update server-side position (use teleportPosition to properly update server state)
            transformComponent.teleportPosition(constrainedLocation)

            // Get or create PendingTeleport component
            val pendingTeleport = commandBuffer.ensureAndGetComponent(ref, PendingTeleport.getComponentType())

            // Queue teleport
            val teleport = Teleport.createForPlayer(constrainedLocation, currentRotation)
            val teleportId = pendingTeleport.queueTeleport(teleport)

            // Send ClientTeleport packet to force client back within radius
            val teleportPacket = ClientTeleport(
                teleportId.toByte(),
                ModelTransform(
                    PositionUtil.toPositionPacket(constrainedLocation),
                    PositionUtil.toDirectionPacket(currentRotation), // Body rotation
                    PositionUtil.toDirectionPacket(currentRotation)  // Head rotation
                ),
                false // Don't reset velocity
            )

            playerRefComponent.packetHandler.write(teleportPacket)

            Log.verbose("RadiusConstraint",
                "Player exceeded ${MAX_DISTANCE_FROM_BODY}m radius, teleported back")
        }
    }
}
