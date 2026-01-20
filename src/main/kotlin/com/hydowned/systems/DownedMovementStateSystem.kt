package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Forces downed players to have sleeping = true in MovementStates.
 *
 * Runs every tick to continuously force sleeping state, preventing the server's
 * movement state system from reverting downed players to idle/standing.
 */
class DownedMovementStateSystem(
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
        // Get movement states component
        val movementStatesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType())
            ?: return

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
    }
}
