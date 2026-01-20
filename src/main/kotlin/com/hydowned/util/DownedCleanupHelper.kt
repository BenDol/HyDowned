package com.hydowned.util

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.modules.interaction.Interactions
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.network.DownedStateTracker
import com.hydowned.util.Log


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
        Log.verbose("CleanupHelper", "============================================")
        Log.verbose("CleanupHelper", "Executing death: $reason")
        Log.verbose("CleanupHelper", "============================================")

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
                    Log.verbose("CleanupHelper", "Forced health to 0 (logout scenario)")
                } else {
                    Log.warning("CleanupHelper", "EntityStatMap not found, cannot force health to 0")
                }
            } else {
                // For timer expiry: use damage system for proper death flow
                val damageCauseIndex = if (downedComponent.originalDamageCause != null) {
                    downedComponent.originalDamage?.damageCauseIndex ?: 0
                } else {
                    0 // Default fallback
                }

                val killDamage = Damage(
                    downedComponent.originalDamage?.source ?: Damage.NULL_SOURCE,
                    damageCauseIndex,
                    999999.0f // Massive damage to ensure death
                )

                // Execute death - this will add DeathComponent and trigger normal death flow
                DamageSystems.executeDamage(ref, commandBuffer, killDamage)

                Log.verbose("CleanupHelper", "Death damage executed")
            }

            // Clean up downed state AFTER death is processed
            cleanupDownedState(ref, commandBuffer, downedComponent, forceHealthToZero)

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
        Log.verbose("CleanupHelper", "============================================")
        Log.verbose("CleanupHelper", "Executing revive")
        Log.verbose("CleanupHelper", "============================================")

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
                Log.verbose("CleanupHelper", "Restored health to ${restoreAmount} (${healthPercent * 100}%)")
            } else {
                Log.warning("CleanupHelper", "Health stat not found")
                return false
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
                Log.verbose("CleanupHelper", "Teleported player back to downed location")
            } else {
                Log.warning("CleanupHelper", "TransformComponent not found")
            }
        } else {
            Log.warning("CleanupHelper", "Downed location not set")
        }
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
        // If this is a logout, do additional cleanup
        // (Component callbacks don't fire during entity removal)
        if (isLogout) {
            // 1. Remove phantom body
            val phantomBodyRef = downedComponent.phantomBodyRef
            if (phantomBodyRef != null && phantomBodyRef.isValid) {
                commandBuffer.removeEntity(phantomBodyRef, RemoveReason.UNLOAD)
                Log.verbose("CleanupHelper", "Manually removed phantom body entity (logout scenario)")
            } else {
                Log.warning("CleanupHelper", "Phantom body ref is null or invalid")
            }

            // 2. Restore player scale (CRITICAL: onComponentRemoved doesn't fire during logout)
            try {
                val scaleComponent = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType())
                if (scaleComponent != null) {
                    scaleComponent.scale = downedComponent.originalScale
                    Log.verbose("CleanupHelper", "Restored scale to ${downedComponent.originalScale} (logout scenario)")
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
                    Log.verbose("CleanupHelper", "Restored original DisplayNameComponent (logout scenario)")
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
                    Log.verbose("CleanupHelper", "Removed HiddenFromAdventurePlayers (logout scenario)")
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
                    Log.verbose("CleanupHelper", "Re-enabled character collisions (logout scenario)")
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
            Log.verbose("CleanupHelper", "Ensured Interactable component exists (logout scenario)")
        }

        // Remove downed component (triggers visibility restoration + phantom body removal for non-logout)
        commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

        // Clear downed state tracking
        DownedStateTracker.setNotDowned(ref)

        Log.verbose("CleanupHelper", "Cleaned up DownedComponent and state tracker")
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
        Log.verbose("CleanupHelper", "Played death animation")

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

            Log.verbose("CleanupHelper", "Set movement state to sleeping")
        } else {
            Log.warning("CleanupHelper", "MovementStatesComponent not found")
        }
    }
}
