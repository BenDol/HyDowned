package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Suppresses ALL healing for downed players by:
 * 1. Monitoring health stat changes every tick
 * 2. If health exceeds 1 HP, immediately revert it back to 1 HP
 * 3. This blocks:
 *    - Natural health regeneration
 *    - Food/item healing
 *    - Potion effects
 *    - Any other healing source
 *
 * Combined with DownedInteractionBlockingSystem, this ensures downed players
 * cannot heal themselves in any way.
 */
class DownedHealingSuppressionSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        EntityStatMap.getComponentType()
    )

    
    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get entity stats
        val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
            ?: return

        // Get health stat
        val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
            ?: return

        val currentHealth = healthStat.get()

        // If health exceeds 1 HP, something tried to heal the player
        if (currentHealth > 1.0f) {
            // Revert health back to 1 HP
            entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), 1.0f)

            // Log the healing attempt
            val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
            println("[HyDowned] ============================================")
            println("[HyDowned] HEALING BLOCKED - Player is downed")
            println("[HyDowned]   Player: ${playerComponent?.displayName}")
            println("[HyDowned]   Attempted health: $currentHealth HP")
            println("[HyDowned]   Reverted to: 1 HP")
            println("[HyDowned] ============================================")
        }
    }
}
