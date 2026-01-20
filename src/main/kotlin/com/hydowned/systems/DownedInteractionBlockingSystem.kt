package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.InteractionManager
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Blocks all interactions for downed players by clearing their InteractionManager
 * This prevents them from:
 * - Breaking blocks
 * - Placing blocks
 * - Using items
 * - Interacting with entities
 * - Opening chests/containers
 * - Any other interaction while downed
 *
 * Uses the same approach as DeathSystems.ClearInteractions
 */
class DownedInteractionBlockingSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val interactionManagerComponentType: ComponentType<EntityStore, InteractionManager> =
        InteractionModule.get().interactionManagerComponent

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        interactionManagerComponentType
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get the InteractionManager component and clear it to block all interactions
        val interactionManager = archetypeChunk.getComponent(index, interactionManagerComponentType)
        if (interactionManager != null) {
            interactionManager.clear()
        }
    }
}
