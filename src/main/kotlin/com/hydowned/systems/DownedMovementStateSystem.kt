package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Forces downed players to have sleeping = true in MovementStates.
 *
 * Uses RefChangeSystem to immediately set sleeping state when downed,
 * and EntityTickingSystem to maintain it every tick.
 */
class DownedMovementStateSystem(
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
        // Force sleeping state immediately when downed
        val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
            ?: return

        val states = movementStatesComponent.movementStates
        val sentStates = movementStatesComponent.sentMovementStates

        // Set current states to sleeping
        states.sleeping = true
        states.idle = false
        states.walking = false
        states.running = false
        states.sprinting = false
        states.jumping = false
        states.falling = false
        states.crouching = false
        states.forcedCrouching = false

        // Force sentStates to be different so MovementStatesSystems.TickingSystem detects change
        sentStates.sleeping = false
        sentStates.idle = true

        println("[HyDowned] [MovementState] Forced sleeping state for downed player")
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
        // Clear sleeping state when revived
        val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
            ?: return

        val states = movementStatesComponent.movementStates
        states.sleeping = false
        states.idle = true

        println("[HyDowned] [MovementState] Cleared sleeping state for revived player")
    }
}
