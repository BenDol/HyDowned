package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
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
import com.hydowned.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Intercepts damage that would kill a player and puts them in downed state instead
 *
 * This system runs in the FilterDamageGroup BEFORE ApplyDamage, so we can:
 * 1. Check if damage would be lethal
 * 2. Modify damage to leave player at configured downed health (default 1% of max HP)
 * 3. Add DownedComponent instead of letting DeathComponent be added
 */
class DownedDeathInterceptor(
    private val config: DownedConfig
) : DamageEventSystem() {

    companion object {
        // Track players being downed RIGHT NOW to prevent race conditions from spam attacks
        // This set is used by DamageImmunitySystem to block damage during initial downing
        // Thread-safe set in case damage systems run concurrently
        val downedThisTick = ConcurrentHashMap.newKeySet<com.hypixel.hytale.component.Ref<EntityStore>>()!!
    }

    // CRITICAL: Query ONLY requires Player component!
    // If we require EntityStatMap/TransformComponent in the query, the system won't run
    // for entities missing those components during cleanup/transition, and damage bypasses us!
    // Instead, we check for these components inside the handler and fail gracefully.
    private val query = Query.and(
        Player.getComponentType()
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
        val ref = archetypeChunk.getReferenceTo(index)
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())

        // CRITICAL: Check if we already processed a fatal hit for this player recently
        // This prevents race conditions from spam attacks in the same tick
        if (downedThisTick.contains(ref)) {
            // Player is currently being downed - verify health is at safe level
            val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
            if (entityStatMap != null) {
                val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
                if (healthStat != null) {
                    val currentHealth = healthStat.get()
                    val downedHealth = (healthStat.max * config.downedHealthPercent.toFloat()).coerceAtLeast(0.1f)

                    if (currentHealth >= downedHealth) {
                        // Health is confirmed safe - remove from tracking, let DamageImmunitySystem handle future damage
                        downedThisTick.remove(ref)
                        // Check if DownedComponent added yet to decide if we should pass through
                        if (commandBuffer.getArchetype(ref).contains(DownedComponent.getComponentType())) {
                            return // Let DamageImmunitySystem handle it
                        }
                        // DownedComponent not added yet, continue processing below
                    } else {
                        // Health still too low - block damage and keep in tracking
                        damage.amount = 0.0f
                        return
                    }
                } else {
                    // No health stat - block as safety measure
                    damage.amount = 0.0f
                    return
                }
            } else {
                // No stat map - block as safety measure
                damage.amount = 0.0f
                return
            }
        }

        // Check if player already downed (or being downed in this tick's command buffer)
        if (commandBuffer.getArchetype(ref).contains(DownedComponent.getComponentType())) {
            // Player is already downed with DownedComponent - they're officially downed now
            // Remove from downedThisTick if present (cleanup for edge cases)
            downedThisTick.remove(ref)
            // Let DamageImmunitySystem handle it (it will check allowedDownedDamage config and timer expiry)
            return
        }

        // Get health stats - MUST be present to process damage
        val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
        if (entityStatMap == null) {
            Log.warning("DeathInterceptor", "[MISSING COMPONENT] ${playerComponent?.displayName} has no EntityStatMap - CANNOT intercept damage!")
            return
        }

        val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
        if (healthStat == null) {
            Log.warning("DeathInterceptor", "[MISSING HEALTH] ${playerComponent?.displayName} has no health stat - CANNOT intercept damage!")
            return
        }

        val currentHealth = healthStat.get()

        // Calculate if this damage would be lethal (bring health to 0 or below)
        val newHealth = currentHealth - damage.amount

        // Check if this damage would kill the player
        if (newHealth <= 0) {
            // This damage would kill - intercept it and restore player to downed health
            // Calculate downed health value based on config (percentage of max health)
            val downedHealth = (healthStat.max * config.downedHealthPercent.toFloat()).coerceAtLeast(0.1f)

            // CRITICAL: Restore health FIRST to prevent death, THEN cancel damage
            // This ensures health is set before any other systems process this damage event
            entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), downedHealth)
            damage.amount = 0.0f

            // IMMEDIATELY mark player as "going down" BEFORE any other code
            // This must happen right after health restore to protect against race conditions
            downedThisTick.add(ref)

            // Don't capture location yet - will be captured after player lands on ground
            // This prevents capturing position mid-air when player dies from fall damage
            // Position will be captured by DownedPlayerModeSyncSystem once onGround = true
            val location = null

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

            // Track downed state for network threads BEFORE adding component
            // This ensures packet handlers see correct state when onComponentAdded fires
            DownedStateTracker.setDowned(ref)

            commandBuffer.addComponent(ref, DownedComponent.getComponentType(), downedComponent)

            // Periodic cleanup to prevent memory leaks from invalid/stale refs
            if (downedThisTick.size > 50) {
                // Remove invalid refs (disconnected players) instead of clearing everything
                downedThisTick.removeIf { !it.isValid }

                // If still too large, clear everything as last resort
                if (downedThisTick.size > 100) {
                    Log.warning("DeathInterceptor", "[CLEANUP] downedThisTick exceeded 100 entries, clearing all")
                    downedThisTick.clear()
                }
            }

            // Notify the downed player
            val downedPlayer = archetypeChunk.getComponent(index, Player.getComponentType())
            downedPlayer?.sendMessage(Message.translation("hydowned.state.knocked_out"))

            // Notify nearby players (use current position even though we're not storing it yet)
            val downedPlayerName = downedPlayer?.displayName ?: "A player"
            val currentTransform = archetypeChunk.getComponent(index, TransformComponent.getComponentType())

            if (currentTransform != null) {
                val currentLocation = currentTransform.position
                val allPlayers = com.hypixel.hytale.server.core.universe.Universe.get().players
                var notifiedCount = 0

                for (nearbyPlayer in allPlayers) {
                    val nearbyRef = nearbyPlayer.reference ?: continue

                    // Skip the downed player themselves
                    if (nearbyRef == ref) {
                        continue
                    }

                    // Get nearby player's position
                    val nearbyTransform = store.getComponent(nearbyRef, TransformComponent.getComponentType())
                        ?: continue
                    val nearbyPos = nearbyTransform.position

                    // Calculate distance
                    val dx = nearbyPos.x - currentLocation.x
                    val dz = nearbyPos.z - currentLocation.z
                    val distanceSquared = dx * dx + dz * dz
                    val notifyRangeSquared = 256.0 * 256.0

                    // Send message if within range
                    if (distanceSquared <= notifyRangeSquared) {
                        nearbyPlayer.sendMessage(
                            Message.translation("hydowned.state.player_knocked_out")
                                .param("playerName", downedPlayerName)
                        )
                        notifiedCount++
                    }
                }
            }
        }
    }
}
