package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log

/**
 * Handles PLAYER mode by putting the downed player into sleep/laying state.
 *
 * PLAYER mode approach:
 * - When player becomes downed: Play death animation, set player to sleeping state
 * - Player character lays down in place (no phantom body needed)
 * - Camera attaches to player's body looking down from above
 * - Movement state is locked to sleeping every tick to prevent client from overriding it
 *
 * This is different from PHANTOM mode where:
 * - Player goes invisible/tiny and can move around
 * - A phantom body NPC is spawned to show the downed location
 */
class DownedPlayerModeSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType(),
        MovementStatesComponent.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only - skip for PHANTOM mode
        if (!config.usePlayerMode) {
            return
        }

        Log.verbose("PlayerMode", "============================================")
        Log.verbose("PlayerMode", "PLAYER MODE: Putting player into sleep state")

        try {
            // Play death animation on the player
            AnimationUtils.playAnimation(
                ref,
                AnimationSlot.Movement,
                "Death",
                true, // sendToSelf
                commandBuffer
            )
            Log.verbose("PlayerMode", "Playing Death animation on player")

            // Set movement state to sleeping (laying down)
            val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
            if (movementStatesComponent != null) {
                val states = movementStatesComponent.movementStates
                val sentStates = movementStatesComponent.sentMovementStates

                // Continuously force sleeping state every tick
                // This prevents the server's movement state system from reverting to idle
                if (!states.sleeping || states.idle || states.walking || states.running || states.sprinting) {
                    states.sleeping = true
                    states.idle = false
                    states.horizontalIdle = false
                    states.walking = false
                    states.running = false
                    states.sprinting = false
                    states.jumping = false
                    states.falling = false
                    states.crouching = false
                    states.forcedCrouching = false
                    states.climbing = false
                    states.flying = false
                    states.swimming = false
                    states.swimJumping = false
                    states.mantling = false
                    states.sliding = false
                    states.mounting = false
                    states.rolling = false
                    states.sitting = false
                    states.gliding = false

                    // Force sentStates to be different so MovementStatesSystems.TickingSystem detects change
                    sentStates.sleeping = false
                    sentStates.idle = true
                }
                Log.verbose("PlayerMode", "Set player to sleeping state (laying down)")
            } else {
                Log.warning("PlayerMode", "MovementStatesComponent not found")
            }

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to set player mode: ${e.message}")
            e.printStackTrace()
        }

        Log.verbose("PlayerMode", "============================================")
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only
        if (!config.usePlayerMode) {
            return
        }

        Log.verbose("PlayerMode", "============================================")
        Log.verbose("PlayerMode", "PLAYER MODE: Removing sleep state from player")

        try {
            // Reset movement state to normal
            val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
            if (movementStatesComponent != null) {
                val states = MovementStates()
                states.sleeping = false
                states.idle = false
                states.onGround = true
                movementStatesComponent.movementStates = states
                movementStatesComponent.sentMovementStates = states
                Log.verbose("PlayerMode", "Reset player to normal movement state")
            }

            // Stop death animation
            AnimationUtils.stopAnimation(
                ref,
                AnimationSlot.Movement,
                commandBuffer
            )
            Log.verbose("PlayerMode", "Stopped Death animation")

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to reset player mode: ${e.message}")
            e.printStackTrace()
        }

        Log.verbose("PlayerMode", "============================================")
    }
}

/**
 * Keeps downed players in sleep state by re-applying it every tick.
 * This prevents the client from overriding the sleep state with normal movement.
 *
 * Only runs in PLAYER mode.
 * Runs AFTER Hytale's MovementStatesSystems to override any state changes.
 */
class DownedPlayerModeSyncSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        MovementStatesComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only - skip for PHANTOM mode
        if (!config.usePlayerMode) {
            return
        }

        val ref = archetypeChunk.getReferenceTo(index)
        val player = archetypeChunk.getComponent(index, Player.getComponentType())

        // Ensure player stays in sleeping state
        val movementStatesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType())
            ?: return

        val states = movementStatesComponent.movementStates
        val sentStates = movementStatesComponent.sentMovementStates

        // Check if any non-sleeping states are active
        val needsReset = !states.sleeping || states.idle || states.walking || states.running ||
                         states.sprinting || !sentStates.sleeping || sentStates.idle ||
                         sentStates.walking || sentStates.running || sentStates.sprinting

        // Log current state for debugging
        if (needsReset) {
            Log.warning("PlayerModeSync", "${player?.displayName} - DETECTED NON-SLEEPING STATE:")
            Log.warning("PlayerModeSync", "  states: sleeping=${states.sleeping}, idle=${states.idle}, walking=${states.walking}, running=${states.running}")
            Log.warning("PlayerModeSync", "  sentStates: sleeping=${sentStates.sleeping}, idle=${sentStates.idle}, walking=${sentStates.walking}, running=${sentStates.running}")
        }

        if (needsReset) {
            // Force both states and sentStates to sleeping
            // This ensures the client receives the correct state
            states.sleeping = true
            states.idle = false
            states.horizontalIdle = false
            states.walking = false
            states.running = false
            states.sprinting = false
            states.jumping = false
            states.falling = false
            states.crouching = false
            states.forcedCrouching = false
            states.climbing = false
            states.flying = false
            states.swimming = false
            states.swimJumping = false
            states.mantling = false
            states.sliding = false
            states.mounting = false
            states.rolling = false
            states.sitting = false
            states.gliding = false

            // Force sentStates to be different so MovementStatesSystems.TickingSystem detects change
            sentStates.sleeping = false
            sentStates.idle = true

            // Update both states using the component methods
            movementStatesComponent.movementStates = states
            movementStatesComponent.sentMovementStates = sentStates

            Log.verbose("PlayerMode", "Force-synced sleeping state (both states and sentStates)")
        }
    }
}
