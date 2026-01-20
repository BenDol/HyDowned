package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Makes downed players immune to all damage
 *
 * This system runs in the FilterDamageGroup BEFORE ApplyDamage, so we can:
 * 1. Check if the player is already downed
 * 2. Cancel all incoming damage by setting amount to 0
 *
 * This prevents:
 * - Taking additional damage while downed
 * - Being "killed" multiple times
 * - Damage affecting the downed state
 */
class DownedDamageImmunitySystem(
    private val config: DownedConfig
) : DamageEventSystem() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        EntityStatMap.getComponentType()
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
        // Check if timer has expired - allow timeout death damage through
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
        if (downedComponent != null && downedComponent.downedTimeRemaining <= 0) {
            // Timer expired - this is the timeout kill damage, allow it through
            val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
            println("[HyDowned] ============================================")
            println("[HyDowned] TIMEOUT KILL DAMAGE - Allowing through")
            println("[HyDowned]   Player: ${playerComponent?.displayName}")
            println("[HyDowned]   Damage: ${damage.amount}")
            println("[HyDowned] ============================================")
            return // Don't block this damage
        }

        // Player is downed but timer hasn't expired - make them immune to all damage
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())

        println("[HyDowned] ============================================")
        println("[HyDowned] DAMAGE BLOCKED - Player is downed")
        println("[HyDowned]   Player: ${playerComponent?.displayName}")
        println("[HyDowned]   Incoming damage: ${damage.amount}")
        println("[HyDowned]   Damage source: ${damage.cause?.id ?: "unknown"}")
        println("[HyDowned] ============================================")

        // Cancel all damage by setting amount to 0
        damage.amount = 0.0f
    }
}
