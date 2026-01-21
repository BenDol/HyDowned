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

    private var lastLogTime = 0L

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

        // Log actual health every 5 seconds (for debugging)
        val now = System.currentTimeMillis()
        if (now - lastLogTime > 5000) {
            val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
            Log.finer("HealingSuppression", "Current server-side health: $currentHealth HP (player: ${playerComponent?.displayName})")
            lastLogTime = now
        }

        // If health exceeds 1 HP, something tried to heal the player
        if (currentHealth > 1.0f) {
            // Revert health back to 1 HP
            entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), 1.0f)

            // Log the healing attempt
            val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
            Log.separator("HealingSuppression")
            Log.finer("HealingSuppression", "HEALING BLOCKED - Player is downed")
            Log.finer("HealingSuppression", "  Player: ${playerComponent?.displayName}")
            Log.finer("HealingSuppression", "  Attempted health: $currentHealth HP")
            Log.finer("HealingSuppression", "  Reverted to: 1 HP")
            Log.separator("HealingSuppression")
        }
    }
}
