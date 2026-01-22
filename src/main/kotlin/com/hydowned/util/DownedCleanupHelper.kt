package com.hydowned.util

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.network.DownedStateTracker


/**
 * Centralized helper for downed state cleanup operations.
 *
 * Provides common housekeeping functions for:
 * - Executing death when timer expires or player logs out
 * - Reviving players
 * - Teleporting back to downed location
 * - Cleaning up downed state
 */
object DownedCleanupHelper {

    /**
     * Executes death for a downed player.
     *
     * This handles:
     * 1. Setting timer to 0 (allows damage immunity system to pass killing damage)
     * 2. Teleporting player back to downed location
     * 3. Executing fatal damage
     * 4. Removing DownedComponent
     * 5. Clearing state tracker
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     * @param downedComponent The downed component
     * @param reason Description of why death is being executed (for logging)
     * @param forceHealthToZero If true, sets health to 0 directly instead of using damage (for logout scenarios)
     */
    fun executeDeath(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        downedComponent: DownedComponent,
        reason: String,
        forceHealthToZero: Boolean = false
    ) {

        // CRITICAL: Set timer to 0 BEFORE executing death
        // This makes the damage immunity system allow the killing damage through
        downedComponent.downedTimeRemaining = 0

        // COMMENTED OUT: This was causing animations to break after respawn
        // The damage/death system handles death animation automatically
        // Set player to death animation/sleeping state before death
        // This ensures they're in the proper state when they die/respawn
        // setDeathAnimationState(ref, commandBuffer)

        // Teleport player back to downed location before death
        teleportToDownedLocation(ref, commandBuffer, downedComponent)

        try {
            if (forceHealthToZero) {
                // For logout scenarios: directly set health to 0
                // This is more reliable than damage during entity removal
                val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
                if (entityStatMap != null) {
                    entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), 0.0f)
                    Log.warning("CleanupHelper", "Forced health to 0 (logout scenario)")
                } else {
                    Log.warning("CleanupHelper", "EntityStatMap not found, cannot force health to 0")
                }

                // Clean up downed state after setting health to 0
                cleanupDownedState(ref, commandBuffer, downedComponent, forceHealthToZero)
            } else {
                // For timer expiry/giveup: directly set health to 0 for reliable death
                // Using damage system is unreliable because of complex interaction with other systems
                Log.finer("CleanupHelper", "Setting health to 0 for timer/giveup death")

                val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
                if (entityStatMap != null) {
                    entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), 0.0f)
                    Log.finer("CleanupHelper", "Health set to 0")
                } else {
                    Log.warning("CleanupHelper", "EntityStatMap not found, cannot set health to 0")
                }

                // Reset camera to normal view BEFORE removing component
                try {
                    val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
                    if (playerRef != null) {
                        val cameraSystem = com.hydowned.HyDownedPlugin.instance?.getCameraSystem()
                        if (cameraSystem != null) {
                            cameraSystem.resetCameraForPlayer(playerRef, commandBuffer)
                            Log.finer("CleanupHelper", "Reset camera to normal view")
                        }
                    }
                } catch (e: Exception) {
                    Log.warning("CleanupHelper", "Failed to reset camera: ${e.message}")
                }

                // Remove DownedComponent and state tracker
                // This will trigger onComponentRemoved callbacks which handle movement state cleanup
                // Clear state tracker FIRST to prevent race condition
                DownedStateTracker.setNotDowned(ref)
                commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

                Log.finer("CleanupHelper", "Removed DownedComponent - player should die from 0 HP")
            }

        } catch (e: Exception) {
            Log.warning("CleanupHelper", "Failed to execute death: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Revives a downed player.
     *
     * This handles:
     * 1. Teleporting player back to downed location
     * 2. Removing DownedComponent
     * 3. Clearing state tracker
     * 4. Restoring health to specified percentage
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     * @param downedComponent The downed component
     * @param healthPercent Percentage of max health to restore (0.0 to 1.0)
     * @return true if revive was successful, false otherwise
     */
    fun executeRevive(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        downedComponent: DownedComponent,
        healthPercent: Double
    ): Boolean {
        Log.finer("CleanupHelper", "============================================")
        Log.finer("CleanupHelper", "Executing revive")
        Log.finer("CleanupHelper", "============================================")

        // Teleport player back to downed location
        teleportToDownedLocation(ref, commandBuffer, downedComponent)

        // CRITICAL: Restore health BEFORE removing DownedComponent
        // This prevents a race condition where another player could attack during revive:
        // - If we remove DownedComponent first, player loses damage immunity
        // - If damage arrives before health is restored, player is at 1 HP and vulnerable
        // - By restoring health first, player is protected until they have sufficient HP
        val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
        if (entityStatMap != null) {
            val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
            if (healthStat != null) {
                val restoreAmount = healthStat.max * healthPercent.toFloat()
                entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), restoreAmount)
                Log.finer("CleanupHelper", "Restored health to ${restoreAmount} (${healthPercent * 100}%)")
            } else {
                Log.warning("CleanupHelper", "Health stat not found")
                return false
            }

            // Restore breath to maximum (prevents drowning death if revived underwater)
            try {
                val breathStat = entityStatMap.get(DefaultEntityStatTypes.getOxygen())
                if (breathStat != null) {
                    entityStatMap.setStatValue(DefaultEntityStatTypes.getOxygen(), breathStat.max)
                    Log.finer("CleanupHelper", "Restored oxygen to maximum (${breathStat.max})")
                } else {
                    Log.warning("CleanupHelper", "Oxygen stat not found - player may drown if underwater")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to restore oxygen: ${e.message}")
            }
        } else {
            Log.warning("CleanupHelper", "EntityStatMap not found")
            return false
        }

        // Now safe to clean up downed state (player has health and can survive attacks)
        cleanupDownedState(ref, commandBuffer, downedComponent, isLogout = false)

        return true
    }

    /**
     * Teleports player back to their downed location.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     * @param downedComponent The downed component containing the downed location
     */
    private fun teleportToDownedLocation(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        downedComponent: DownedComponent
    ) {
        val downedLocation = downedComponent.downedLocation
        if (downedLocation != null) {
            val transformComponent = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
            if (transformComponent != null) {
                transformComponent.teleportPosition(downedLocation)
                Log.finer("CleanupHelper", "Teleported player back to downed location")
            } else {
                Log.warning("CleanupHelper", "TransformComponent not found")
            }
        } else {
            Log.warning("CleanupHelper", "Downed location not set")
        }
    }

    /**
     * Cleans up downed state when a player dies from damage while downed.
     *
     * This handles the case where DeathComponent is added to a downed player
     * (e.g., when allowedDownedDamage permits fatal damage).
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     * @param downedComponent The downed component (to access phantom body ref)
     */
    fun cleanupForDeath(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        downedComponent: DownedComponent
    ) {
        Log.finer("CleanupHelper", "Cleaning up downed state for death (DeathComponent added)")

        // CRITICAL: Refresh cosmetic components BEFORE cleanup
        // This ensures player's skin/model is properly restored on respawn
        try {
            ComponentUtils.refreshComponent(
                ref, commandBuffer,
                com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType(),
                "PlayerSkinComponent",
                "CleanupHelper"
            )

            ComponentUtils.refreshComponent(
                ref, commandBuffer,
                com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                "ModelComponent",
                "CleanupHelper"
            )

            Log.finer("CleanupHelper", "Refreshed cosmetic components for respawn")
        } catch (e: Exception) {
            Log.warning("CleanupHelper", "Failed to refresh cosmetic components: ${e.message}")
        }

        // Use explicit cleanup mode since we're in the middle of death processing
        // and can't rely on normal component removal callbacks
        cleanupDownedState(ref, commandBuffer, downedComponent, isLogout = true)
    }

    /**
     * Removes downed component and clears state tracker.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     * @param downedComponent The downed component (to access phantom body ref)
     * @param isLogout True if this is a logout scenario (needs explicit cleanup)
     */
    private fun cleanupDownedState(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        downedComponent: DownedComponent,
        isLogout: Boolean = false
    ) {
        // 1. Remove phantom body (ALWAYS - needed for both revive and logout)
        val phantomBodyRef = downedComponent.phantomBodyRef
        if (phantomBodyRef != null && phantomBodyRef.isValid) {
            try {
                // Get phantom body network ID before removing
                val phantomNetworkId = commandBuffer.getComponent(phantomBodyRef, NetworkId.getComponentType())

                // Remove phantom body entity
                commandBuffer.removeEntity(phantomBodyRef, RemoveReason.UNLOAD)
                Log.finer("CleanupHelper", "Removed phantom body entity")

                // Clean up state tracker
                if (phantomNetworkId != null) {
                    DownedStateTracker.removePhantomBody(phantomNetworkId.id)
                    Log.finer("CleanupHelper", "Cleaned up phantom body from state tracker")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to remove phantom body: ${e.message}")
                e.printStackTrace()
            }
        }

        // If this is a logout, do additional cleanup
        // (Component callbacks don't fire during entity removal)
        if (isLogout) {

            // 2. Restore player scale (CRITICAL: onComponentRemoved doesn't fire during logout)
            try {
                val scaleComponent = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType())
                if (scaleComponent != null) {
                    scaleComponent.scale = downedComponent.originalScale
                    Log.finer("CleanupHelper", "Restored scale to ${downedComponent.originalScale} (logout scenario)")
                } else {
                    Log.warning("CleanupHelper", "EntityScaleComponent not found, cannot restore scale")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to restore scale: ${e.message}")
                e.printStackTrace()
            }

            // 3. Restore original DisplayNameComponent (logout scenario)
            // Replace empty DisplayNameComponent with original
            try {
                val originalDisplayName = downedComponent.originalDisplayName
                if (originalDisplayName != null) {
                    commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())
                    commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)
                    Log.finer("CleanupHelper", "Restored original DisplayNameComponent (logout scenario)")
                } else {
                    // No original stored - ensure component exists
                    commandBuffer.ensureComponent(ref, DisplayNameComponent.getComponentType())
                    Log.warning("CleanupHelper", "No original DisplayNameComponent, ensured one exists")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to restore DisplayNameComponent: ${e.message}")
                e.printStackTrace()
            }

            // 4. Restore visibility (INVISIBLE mode - remove HiddenFromAdventurePlayers)
            // onComponentRemoved doesn't fire during logout, so we must manually restore
            try {
                if (downedComponent.wasVisibleBefore) {
                    commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers.getComponentType())
                    Log.finer("CleanupHelper", "Removed HiddenFromAdventurePlayers (logout scenario)")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to remove HiddenFromAdventurePlayers: ${e.message}")
                e.printStackTrace()
            }

            // 5. Restore collision (re-enable character collisions)
            // onComponentRemoved doesn't fire during logout, so we must manually restore
            try {
                val collisionResult = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent.getComponentType())
                if (collisionResult != null && downedComponent.hadCollisionEnabled) {
                    // Note: API method has typo - "Collsions" instead of "Collisions"
                    collisionResult.collisionResult.enableCharacterCollsions()
                    Log.finer("CleanupHelper", "Re-enabled character collisions (logout scenario)")
                }
            } catch (e: Exception) {
                Log.warning("CleanupHelper", "Failed to re-enable character collisions: ${e.message}")
                e.printStackTrace()
            }

            // 5b. Remove any lingering Intangible component (defensive cleanup for old versions)
            try {
                commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.Intangible.getComponentType())
            } catch (e: Exception) {
                // Ignore - component may not exist
            }

            // 6. Restore Interactable component
            // DownedRemoveInteractionsSystem removes this when downed, but won't restore it during logout
            // Ensuring it exists allows the player to interact after respawn
            commandBuffer.ensureComponent(ref, Interactable.getComponentType())
            Log.finer("CleanupHelper", "Ensured Interactable component exists (logout scenario)")
        }

        // Reset camera to normal view
        try {
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            if (playerRef != null) {
                val cameraSystem = com.hydowned.HyDownedPlugin.instance?.getCameraSystem()
                if (cameraSystem != null) {
                    cameraSystem.resetCameraForPlayer(playerRef, commandBuffer)
                    Log.finer("CleanupHelper", "Reset camera to normal view")
                } else {
                    Log.warning("CleanupHelper", "Camera system not available")
                }
            } else {
                Log.warning("CleanupHelper", "PlayerRef not found, cannot reset camera")
            }
        } catch (e: Exception) {
            Log.warning("CleanupHelper", "Failed to reset camera: ${e.message}")
            e.printStackTrace()
        }

        // CRITICAL: Stop animations BEFORE removing component
        // This prevents AnimationLoop from sending one final Sleep animation during revive
        // MUST stop BOTH Death and Sleep animations (AnimationLoop sends Sleep, not Death)
        try {
            AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, commandBuffer)

            // Also send explicit stop packet for Sleep animation to player's client
            // AnimationUtils.stopAnimation might only stop Death, but client has Sleep playing
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            if (playerRef != null) {
                val stopAnimationPacket = com.hypixel.hytale.protocol.packets.entities.PlayAnimation()
                val networkIdComp = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType())
                if (networkIdComp != null) {
                    stopAnimationPacket.entityId = networkIdComp.id
                    stopAnimationPacket.slot = AnimationSlot.Movement
                    stopAnimationPacket.animationId = null // null = stop animation
                    playerRef.packetHandler.writeNoCache(stopAnimationPacket)
                    Log.finer("CleanupHelper", "Sent explicit Sleep animation stop packet to client")
                }
            }

            Log.finer("CleanupHelper", "Stopped animations before component removal")
        } catch (e: Exception) {
            Log.warning("CleanupHelper", "Failed to stop animations: ${e.message}")
        }

        // CRITICAL: Send movement state reset directly to player's client BEFORE clearing state tracker
        // This ensures the packet goes through while we can still identify it as a revive packet
        try {
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            val networkIdComponent = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType())
            val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())

            if (playerRef != null && networkIdComponent != null && movementStatesComponent != null) {
                // Set movement states on server first
                val newStates = com.hypixel.hytale.protocol.MovementStates()
                newStates.sleeping = false
                newStates.idle = true
                newStates.onGround = true

                val oldSentStates = com.hypixel.hytale.protocol.MovementStates()
                oldSentStates.sleeping = true
                oldSentStates.idle = false
                oldSentStates.onGround = true

                movementStatesComponent.movementStates = newStates
                movementStatesComponent.sentMovementStates = oldSentStates

                // Send packet to player's own client
                val componentUpdate = com.hypixel.hytale.protocol.ComponentUpdate()
                componentUpdate.type = com.hypixel.hytale.protocol.ComponentUpdateType.MovementStates
                componentUpdate.movementStates = newStates

                val entityUpdate = com.hypixel.hytale.protocol.EntityUpdate()
                entityUpdate.networkId = networkIdComponent.id
                entityUpdate.updates = arrayOf(componentUpdate)

                val entityUpdatesPacket = com.hypixel.hytale.protocol.packets.entities.EntityUpdates(
                    null,
                    arrayOf(entityUpdate)
                )

                playerRef.packetHandler.writeNoCache(entityUpdatesPacket)
                Log.finer("CleanupHelper", "Sent movement state reset to player's client (sleeping=false, idle=true)")
            }
        } catch (e: Exception) {
            Log.warning("CleanupHelper", "Failed to send movement state reset: ${e.message}")
            e.printStackTrace()
        }

        // CRITICAL: Clear state tracker AFTER sending movement state update
        // This ensures the packet goes through while we can still identify it
        DownedStateTracker.setNotDowned(ref)

        // Remove downed component (triggers visibility restoration + phantom body removal for non-logout)
        commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

        Log.finer("CleanupHelper", "Cleaned up DownedComponent and state tracker")
    }

    /**
     * Sets player to death animation and sleeping movement state.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component operations
     */
    private fun setDeathAnimationState(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Play death animation
        AnimationUtils.playAnimation(
            ref,
            AnimationSlot.Movement,
            "Death",
            true, // sendToSelf
            commandBuffer
        )
        Log.finer("CleanupHelper", "Played death animation")

        // Set movement state to sleeping
        val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
        if (movementStatesComponent != null) {
            val states = movementStatesComponent.movementStates
            states.sleeping = true
            states.idle = false
            states.walking = false
            states.running = false
            states.sprinting = false
            states.jumping = false
            states.falling = false
            states.crouching = false
            states.onGround = true

            // Force sentStates to be different so change is detected
            val sentStates = movementStatesComponent.sentMovementStates
            sentStates.sleeping = false
            sentStates.idle = true

            Log.finer("CleanupHelper", "Set movement state to sleeping")
        } else {
            Log.warning("CleanupHelper", "MovementStatesComponent not found")
        }
    }
}
