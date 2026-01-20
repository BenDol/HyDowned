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
            Log.debug("DeathInterceptor", "Player already downed, allowing damage through")
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
            Log.separator("DeathInterceptor")
            Log.verbose("DeathInterceptor", "Intercepting lethal damage!")
            Log.debug("DeathInterceptor", "Current Health: $currentHealth")
            Log.debug("DeathInterceptor", "Damage Amount: ${damage.amount}")
            Log.debug("DeathInterceptor", "Would be fatal: $newHealth HP")
            Log.separator("DeathInterceptor")

            // Modify damage to leave player at 1 HP instead of 0
            val modifiedDamage = currentHealth - 1.0f
            damage.amount = modifiedDamage

            Log.verbose("DeathInterceptor", "Modified damage to: $modifiedDamage (leaves player at 1 HP)")
            Log.debug("DeathInterceptor", "Calculated final health: ${currentHealth - modifiedDamage} HP")
            Log.debug("DeathInterceptor", "Current damage.amount after modification: ${damage.amount}")

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

            Log.info("DeathInterceptor", "Player entered downed state")
            Log.debug("DeathInterceptor", "Timer: ${config.downedTimerSeconds} seconds")
            Log.debug("DeathInterceptor", "Location: $location")
            Log.separator("DeathInterceptor")

            // Notify nearby players
            val downedPlayer = archetypeChunk.getComponent(index, Player.getComponentType())
            val downedPlayerName = downedPlayer?.displayName ?: "A player"

            if (location != null) {
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
                    val dx = nearbyPos.x - location.x
                    val dz = nearbyPos.z - location.z
                    val distanceSquared = dx * dx + dz * dz
                    val notifyRangeSquared = 256.0 * 256.0

                    // Send message if within range
                    if (distanceSquared <= notifyRangeSquared) {
                        nearbyPlayer.sendMessage(Message.raw("$downedPlayerName is downed - crouch near their body to revive"))
                        notifiedCount++
                    }
                }

                Log.verbose("DeathInterceptor", "Notified $notifiedCount nearby players")
            }
        }
    }
}
