package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Removes items from hand while downed to prevent combat/interaction crashes
 */
class DownedDisableItemsSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
            ?: return

        val inventory = playerComponent.inventory

        // Force activeHotbarSlot to -1 (no slot selected)
        // This should prevent client from trying to use any items
        if (inventory.activeHotbarSlot != (-1).toByte()) {
            inventory.activeHotbarSlot = (-1).toByte()
        }
    }
}
