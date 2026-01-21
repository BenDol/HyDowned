package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.ApplyLookType
import com.hypixel.hytale.protocol.AttachedToType
import com.hypixel.hytale.protocol.ClientCameraView
import com.hypixel.hytale.protocol.Direction
import com.hypixel.hytale.protocol.MouseInputTargetType
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.Position
import com.hypixel.hytale.protocol.PositionType
import com.hypixel.hytale.protocol.RotationType
import com.hypixel.hytale.protocol.ServerCameraSettings
import com.hypixel.hytale.protocol.Vector2f
import com.hypixel.hytale.protocol.Vector3f
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Controls the camera for downed players to look down at their phantom body.
 *
 * This system runs periodically (every 0.2 seconds) and checks if downed players
 * have their phantom body ready. Once ready, it attaches the camera to the phantom body.
 *
 * When a player becomes downed:
 * - Waits for phantom body to spawn and get NetworkId
 * - Attaches camera to phantom body entity
 * - Camera positioned 5 blocks above, looking down
 * - Camera is locked in this view (player can't move it)
 *
 * When player is revived or dies:
 * - Camera is reset to normal first-person view
 */
class DownedCameraSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(0.2f) { // Check every 0.2 seconds

    // Track which players have had their camera set up
    private val cameraSetupComplete = ConcurrentHashMap.newKeySet<String>()

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
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
        // PLAYER mode only - skip camera system for PHANTOM mode
        if (!config.usePlayerMode) {
            return
        }

        val ref = archetypeChunk.getReferenceTo(index)
        val playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return

        val playerUuid = playerRef.uuid.toString()

        // Skip if camera already set up for this player
        if (cameraSetupComplete.contains(playerUuid)) {
            return
        }

        // PLAYER mode: Attach camera to player's own entity (they're laying down in place)
        // Get player's NetworkId for camera attachment
        val playerNetworkId = commandBuffer.getComponent(ref, NetworkId.getComponentType())
        if (playerNetworkId == null) {
            // Player doesn't have NetworkId yet, will check again next tick
            return
        }

        val targetEntityId = playerNetworkId.id

        try {
            Log.finer("DownedCamera", "============================================")
            Log.finer("DownedCamera", "PLAYER MODE: Setting up camera for ${playerRef.username}")
            Log.finer("DownedCamera", "Attaching camera to player's own entity ID: $targetEntityId")

            // Create camera settings to look down at player's body from above (top-down view)
            val cameraSettings = ServerCameraSettings().apply {
                // Smooth interpolation for camera movement
                positionLerpSpeed = 0.3f  // Slow smooth pan
                rotationLerpSpeed = 0.3f  // Slow smooth rotation

                // Attach camera to the player's entity
                attachedToType = AttachedToType.EntityId
                attachedToEntityId = targetEntityId  // Player's entity ID
                eyeOffset = false

                // Position: Place camera 5 blocks ABOVE the player's body
                positionType = PositionType.AttachedToPlusOffset
                positionOffset = Position(
                    0.0,     // x: no horizontal offset (centered on player)
                    5.0,     // y: 5 blocks ABOVE player
                    0.0      // z: no horizontal offset (centered on player)
                )

                // Rotation: Look straight DOWN at the player's body (top-down view)
                // Use Custom rotation for absolute angle, not relative to player's rotation
                rotationType = RotationType.Custom
                rotation = Direction(
                    0f,     // yaw (0 = north)
                    -89f,  // pitch (-90 = straight down, -89 for slight angle)
                    0f      // roll (0 = no tilt)
                )

                // Lock camera rotation (player can't move it)
                applyLookType = ApplyLookType.Rotation
                allowPitchControls = false  // Disable pitch controls

                // Disable look input completely
                lookMultiplier = Vector2f(0f, 0f)

                // Third-person view to see the body below
                isFirstPerson = false

                // Small distance for proper top-down view
                distance = 1.0f

                // Keep reticule hidden, hide cursor too (can't interact anyway)
                displayReticle = false
                displayCursor = false

                // CRITICAL: Disable all character movement and physics
                skipCharacterPhysics = true  // Disable physics system
                speedModifier = 0f  // Set movement speed to 0
                movementMultiplier = Vector3f(0f, 0f, 0f)  // Lock all axes

                // Disable all mouse input
                mouseInputTargetType = MouseInputTargetType.None
                sendMouseMotion = false
            }

            // Create and send SetServerCamera packet
            val packet = SetServerCamera(
                ClientCameraView.Custom,
                true,  // isLocked - Lock camera to this view
                cameraSettings
            )

            // Send packet to player
            playerRef.packetHandler.writeNoCache(packet as Packet)

            // Mark camera as set up for this player
            cameraSetupComplete.add(playerUuid)

            Log.finer("DownedCamera", "Camera attached to player's entity (ID: $targetEntityId), positioned 5 blocks above, looking down at -89° pitch (top-down view)")
            Log.finer("DownedCamera", "============================================")

        } catch (e: Exception) {
            Log.warning("DownedCamera", "Failed to set camera: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Reset camera when player is no longer downed
     * Called from DownedCleanupHelper or when DownedComponent is removed
     */
    fun resetCameraForPlayer(playerRef: PlayerRef, commandBuffer: CommandBuffer<EntityStore>) {
        val playerUuid = playerRef.uuid.toString()

        // Remove from tracking set
        cameraSetupComplete.remove(playerUuid)

        try {
            Log.finer("DownedCamera", "============================================")
            Log.finer("DownedCamera", "Resetting camera to normal first-person view for ${playerRef.username}")

            val playerEntityRef = playerRef.reference
            if (playerEntityRef != null) {
                // Get CameraManager and reset camera
                val cameraManager = commandBuffer.getComponent(playerEntityRef, CameraManager.getComponentType())
                if (cameraManager != null) {
                    cameraManager.resetCamera(playerRef)
                    Log.finer("DownedCamera", "Camera reset to normal first-person view")
                } else {
                    Log.warning("DownedCamera", "CameraManager not found")
                }
            }

            Log.finer("DownedCamera", "============================================")
        } catch (e: Exception) {
            Log.warning("DownedCamera", "Failed to reset camera: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Clear camera tracking state without resetting camera
     * Used during login cleanup where camera should already be in normal state
     */
    fun clearCameraTracking(playerRef: PlayerRef) {
        val playerUuid = playerRef.uuid.toString()
        val wasTracked = cameraSetupComplete.remove(playerUuid)
        if (wasTracked) {
            Log.finer("DownedCamera", "Cleared camera tracking for ${playerRef.username}")
        }
    }

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
        // Run in parallel for better performance
        return true
    }
}
