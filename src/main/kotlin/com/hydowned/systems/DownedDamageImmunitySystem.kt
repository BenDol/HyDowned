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
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


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

    // CRITICAL: Query ONLY requires Player + DownedComponent!
    // If we require EntityStatMap in the query, the system won't run
    // for entities missing that component during cleanup/transition, and damage bypasses us!
    // Instead, we check for EntityStatMap inside the handler.
    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType()
    )

    // Run in FilterDamageGroup, BEFORE ApplyDamage
    private val dependencies = setOf(
        SystemDependency(Order.BEFORE, DamageSystems.ApplyDamage::class.java)
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
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())

        // Note: Race condition protection is handled in DeathInterceptor
        // By the time we reach here, player has DownedComponent and health is confirmed safe

        // Check if this is intentional death damage (from executeDeath) - allow it through
        if (damage.amount >= 999999.0f) {
            return
        }

        // Check if timer has expired - allow timeout death damage through
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
        if (downedComponent == null) {
            Log.warning("DamageImmunity", "[MISSING COMPONENT] ${playerComponent?.displayName} has no DownedComponent - this shouldn't happen!")
            return // Allow damage through since player isn't actually downed
        }

        if (downedComponent.downedTimeRemaining <= 0) {
            // Timer expired - allow kill damage through
            return
        }

        // Check damage source and config to determine if damage should be allowed
        val damageSource = damage.source
        try {
            // Check if damage is from an entity (player or mob)
            // EntitySource includes both direct melee and ProjectileSource (which extends EntitySource)
            if (damageSource is Damage.EntitySource) {
                val sourceEntityRef = damageSource.ref

                // Check if source entity is a player
                val sourcePlayerComponent = store.getComponent(sourceEntityRef, Player.getComponentType())
                if (sourcePlayerComponent != null) {
                    // Player damage (PvP)
                    if (config.allowedDownedDamage.player) {
                        return // Allow player damage through
                    }
                } else {
                    // Entity damage but not from a player (mob damage)
                    if (config.allowedDownedDamage.mob) {
                        return // Allow mob damage through
                    }
                }
            } else if (damageSource is Damage.EnvironmentSource) {
                // Check if it's specifically lava damage
                if (damageSource.type.contains("lava", ignoreCase = true)) {
                    if (config.allowedDownedDamage.lava) {
                        return // Allow lava damage through
                    }
                } else {
                    // Other environmental damage (fall, drowning, fire, etc.)
                    if (config.allowedDownedDamage.environment) {
                        return // Allow environmental damage through
                    }
                }
            }
        } catch (e: Exception) {
            Log.warning("DamageImmunity", "Failed to check damage source: ${e.message}")
            e.printStackTrace()
            // On error, continue to block damage (safer default)
        }

        // Cancel all damage by setting amount to 0
        damage.amount = 0.0f
    }
}
