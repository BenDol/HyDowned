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
import com.hydowned.util.Log
import java.util.logging.Level


/**
 * Suppresses ALL healing for downed players by:
 * 1. Monitoring health stat changes every tick
 * 2. If health exceeds downed health value (configurable), immediately revert it back
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

        // Calculate downed health value based on config (percentage of max health)
        val downedHealth = (healthStat.max * config.downedHealthPercent.toFloat()).coerceAtLeast(0.1f)

        // If health exceeds downed health, something tried to heal the player
        if (currentHealth > downedHealth) {
            // Revert health back to downed health value
            entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), downedHealth)
        }
    }
}
