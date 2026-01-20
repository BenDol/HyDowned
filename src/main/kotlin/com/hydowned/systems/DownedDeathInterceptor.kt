package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.network.DownedStateTracker

/**
 * Intercepts damage that would kill a player and puts them in downed state instead
 *
 * This system runs in the FilterDamageGroup BEFORE ApplyDamage, so we can:
 * 1. Check if damage would be lethal
 * 2. Modify damage to leave player at 1 HP
 * 3. Add DownedComponent instead of letting DeathComponent be added
 */
class DownedDeathInterceptor(
    private val config: DownedConfig
) : DamageEventSystem() {

    private val query = Query.and(
        Player.getComponentType(),
        EntityStatMap.getComponentType(),
        TransformComponent.getComponentType()
    )

    // Run in FilterDamageGroup, BEFORE ApplyDamage
    private val dependencies = setOf(
        SystemDependency<EntityStore, DamageSystems.ApplyDamage>(Order.BEFORE, DamageSystems.ApplyDamage::class.java)
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun getGroup(): SystemGroup<EntityStore>? {
        return DamageModule.get().filterDamageGroup
    }

    override fun getDependencies(): Set<Dependency<EntityStore>> = dependencies

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        damage: Damage
    ) {
        val ref = archetypeChunk.getReferenceTo(index)

        // Skip if player already downed
        if (commandBuffer.getArchetype(ref).contains(DownedComponent.getComponentType())) {
            println("[HyDowned] Player already downed, allowing damage through")
            return
        }

        // Get health stats
        val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
            ?: return
        val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth()) ?: return
        val currentHealth = healthStat.get()

        // Calculate if this damage would be lethal
        val newHealth = currentHealth - damage.amount

        if (newHealth <= healthStat.min) {
            // This damage would kill the player - intercept it!
            println("[HyDowned] ============================================")
            println("[HyDowned] Intercepting lethal damage!")
            println("[HyDowned]   Current Health: $currentHealth")
            println("[HyDowned]   Damage Amount: ${damage.amount}")
            println("[HyDowned]   Would be fatal: $newHealth HP")
            println("[HyDowned] ============================================")

            // Modify damage to leave player at 1 HP instead of 0
            val modifiedDamage = currentHealth - 1.0f
            damage.amount = modifiedDamage

            println("[HyDowned] Modified damage to: $modifiedDamage (leaves player at 1 HP)")

            // Get location for downed state
            val transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType())
            val location = transform?.position?.clone()

            // Add downed component instead of letting death happen
            val downedComponent = DownedComponent(
                downedTimeRemaining = config.downedTimerSeconds,
                reviverPlayerIds = mutableSetOf(),
                reviveTimeRemaining = 0.0,
                downedAt = System.currentTimeMillis(),
                downedLocation = location,
                originalDamageCause = null, // Will use damageCauseIndex instead
                originalDamage = damage
            )

            commandBuffer.addComponent(ref, DownedComponent.getComponentType(), downedComponent)

            // Track downed state for network threads
            DownedStateTracker.setDowned(ref)

            println("[HyDowned] ✓ Player entered downed state")
            println("[HyDowned]   Timer: ${config.downedTimerSeconds} seconds")
            println("[HyDowned]   Location: $location")
            println("[HyDowned] ============================================")

            // TODO: Apply visual effects (animation, particles, etc.)
            // TODO: Apply movement speed reduction
            // TODO: Send feedback message to player
        }
    }
}
