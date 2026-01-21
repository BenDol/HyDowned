package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log

/**
 * CRITICAL: Blocks player movement by clearing PlayerInput queue BEFORE it's processed.
 *
 * This system runs BEFORE PlayerSystems.ProcessPlayerInput using system dependencies.
 * It clears the input queue for downed players, preventing any movement commands
 * from being processed.
 *
 * Also zeroes velocity as backup suppression.
 *
 * Only runs in PLAYER mode.
 */
class DownedMovementSuppressionSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        PlayerInput.getComponentType(),
        Velocity.getComponentType()
    )

    // Run BEFORE ProcessPlayerInput to clear the queue before it's processed
    private val dependencies = setOf<Dependency<EntityStore>>(
        SystemDependency(Order.BEFORE, PlayerSystems.ProcessPlayerInput::class.java)
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun getDependencies(): Set<Dependency<EntityStore>> = dependencies

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only
        if (!config.usePlayerMode) {
            return
        }

        // 1. Selectively filter PlayerInput queue - remove movement but KEEP head/body rotation
        val playerInput = archetypeChunk.getComponent(index, PlayerInput.getComponentType())
        if (playerInput != null) {
            val queue = playerInput.movementUpdateQueue

            // Early exit if queue is empty - avoid filter operation
            if (queue.isEmpty()) {
                return
            }

            // Check if any inputs need to be blocked before creating filtered list
            var hasBlockedInputs = false
            for (input in queue) {
                when (input) {
                    is PlayerInput.WishMovement,
                    is PlayerInput.RelativeMovement,
                    is PlayerInput.AbsoluteMovement,
                    is PlayerInput.SetClientVelocity -> {
                        hasBlockedInputs = true
                        break
                    }
                    is PlayerInput.SetMovementStates -> {
                        if (input.movementStates.crouching || input.movementStates.forcedCrouching) {
                            hasBlockedInputs = true
                            break
                        }
                    }
                }
            }

            // Only filter if there are actually blocked inputs
            if (hasBlockedInputs) {
                // Remove only position-based movements, keep SetHead and SetBody for looking around
                val filtered = queue.filter { input ->
                    when (input) {
                        is PlayerInput.SetHead -> true  // ALLOW head rotation
                        is PlayerInput.SetBody -> true  // ALLOW body rotation
                        is PlayerInput.WishMovement -> false  // BLOCK wish movement
                        is PlayerInput.RelativeMovement -> false  // BLOCK relative movement
                        is PlayerInput.AbsoluteMovement -> false  // BLOCK absolute movement
                        is PlayerInput.SetClientVelocity -> false  // BLOCK velocity
                        is PlayerInput.SetMovementStates -> {
                            // BLOCK crouch states, allow others (for animations)
                            val states = input.movementStates
                            !(states.crouching || states.forcedCrouching)
                        }
                        else -> true  // ALLOW other inputs
                    }
                }

                queue.clear()
                queue.addAll(filtered)
                Log.finer("MovementSuppression", "Filtered ${queue.size - filtered.size} movement inputs from queue")
            }
        }

        // 2. Zero velocity as backup
        val velocity = archetypeChunk.getComponent(index, Velocity.getComponentType())
        velocity?.setZero()
    }
}
