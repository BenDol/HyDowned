package com.hydowned.util

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
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
        println("[HyDowned] [Cleanup] ============================================")
        println("[HyDowned] [Cleanup] Executing death: $reason")
        println("[HyDowned] [Cleanup] ============================================")

        // CRITICAL: Set timer to 0 BEFORE executing death
        // This makes the damage immunity system allow the killing damage through
        downedComponent.downedTimeRemaining = 0

        // Set player to death animation/sleeping state before death
        // This ensures they're in the proper state when they die/respawn
        setDeathAnimationState(ref, commandBuffer)

        // Teleport player back to downed location before death
        teleportToDownedLocation(ref, commandBuffer, downedComponent)

        try {
            if (forceHealthToZero) {
                // For logout scenarios: directly set health to 0
                // This is more reliable than damage during entity removal
                val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
                if (entityStatMap != null) {
                    entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), 0.0f)
                    println("[HyDowned] [Cleanup] ✓ Forced health to 0 (logout scenario)")
                } else {
                    println("[HyDowned] [Cleanup] ⚠ EntityStatMap not found, cannot force health to 0")
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

                println("[HyDowned] [Cleanup] ✓ Death damage executed")
            }

            // Clean up downed state AFTER death is processed
            cleanupDownedState(ref, commandBuffer, downedComponent, forceHealthToZero)

        } catch (e: Exception) {
            println("[HyDowned] [Cleanup] ⚠ Failed to execute death: ${e.message}")
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
        println("[HyDowned] [Cleanup] ============================================")
        println("[HyDowned] [Cleanup] Executing revive")
        println("[HyDowned] [Cleanup] ============================================")

        // Teleport player back to downed location
        teleportToDownedLocation(ref, commandBuffer, downedComponent)

        // Clean up downed state (not logout, so component callbacks will fire)
        cleanupDownedState(ref, commandBuffer, downedComponent, isLogout = false)

        // Restore health
        val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
        if (entityStatMap != null) {
            val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
            if (healthStat != null) {
                val restoreAmount = healthStat.max * healthPercent.toFloat()
                entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), restoreAmount)
                println("[HyDowned] [Cleanup] ✓ Restored health to ${restoreAmount} (${healthPercent * 100}%)")
                return true
            } else {
                println("[HyDowned] [Cleanup] ⚠ Health stat not found")
                return false
            }
        } else {
            println("[HyDowned] [Cleanup] ⚠ EntityStatMap not found")
            return false
        }
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
                println("[HyDowned] [Cleanup] ✓ Teleported player back to downed location")
            } else {
                println("[HyDowned] [Cleanup] ⚠ TransformComponent not found")
            }
        } else {
            println("[HyDowned] [Cleanup] ⚠ Downed location not set")
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
        // ALWAYS restore visibility before cleanup, even on death
        // When player dies and respawns as new entity, the old visibility state persists
        // and the new entity starts invisible
        val playerUuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
        if (playerUuidComponent != null) {
            val playerUuid = playerUuidComponent.uuid
            println("[HyDowned] [Cleanup] Restoring visibility for player: $playerUuid")

            // Show this player to all other players
            val allPlayers = Universe.get().players
            for (otherPlayer in allPlayers) {
                val otherPlayerEntityRef = otherPlayer.reference ?: continue

                // Get the PlayerRef component which has the HiddenPlayersManager
                val otherPlayerRefComponent = commandBuffer.getComponent(otherPlayerEntityRef, PlayerRef.getComponentType())
                    ?: continue

                // Show the player
                otherPlayerRefComponent.getHiddenPlayersManager().showPlayer(playerUuid)
            }

            println("[HyDowned] [Cleanup] ✓ Visibility restored to ${allPlayers.size} players")
        } else {
            println("[HyDowned] [Cleanup] ⚠ UUIDComponent not found, cannot restore visibility")
        }

        // If this is a logout, do additional cleanup
        // (Component callbacks don't fire during entity removal)
        if (isLogout) {
            // 1. Remove phantom body
            val phantomBodyRef = downedComponent.phantomBodyRef
            if (phantomBodyRef != null && phantomBodyRef.isValid) {
                commandBuffer.removeEntity(phantomBodyRef, RemoveReason.UNLOAD)
                println("[HyDowned] [Cleanup] ✓ Manually removed phantom body entity (logout scenario)")
            } else {
                println("[HyDowned] [Cleanup] ⚠ Phantom body ref is null or invalid")
            }

            // 2. Restore Interactable component
            // DownedRemoveInteractionsSystem removes this when downed, but won't restore it during logout
            // Ensuring it exists allows the player to interact after respawn
            commandBuffer.ensureComponent(ref, Interactable.getComponentType())
            println("[HyDowned] [Cleanup] ✓ Ensured Interactable component exists (logout scenario)")
        }

        // Remove downed component (triggers visibility restoration + phantom body removal for non-logout)
        commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

        // Clear downed state tracking
        DownedStateTracker.setNotDowned(ref)

        println("[HyDowned] [Cleanup] ✓ Cleaned up DownedComponent and state tracker")
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
        println("[HyDowned] [Cleanup] ✓ Played death animation")

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

            println("[HyDowned] [Cleanup] ✓ Set movement state to sleeping")
        } else {
            println("[HyDowned] [Cleanup] ⚠ MovementStatesComponent not found")
        }
    }
}
