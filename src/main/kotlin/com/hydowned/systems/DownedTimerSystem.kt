package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.listeners.PlayerInteractListener
import com.hydowned.network.DownedStateTracker

/**
 * System that ticks down the downed timer and executes death when it expires
 *
 * Runs every 1 second (DelayedEntitySystem with 1.0f delay)
 */
class DownedTimerSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(1.0f) {

    private val query = Query.and(
        DownedComponent.getComponentType(),
        Player.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val ref = archetypeChunk.getReferenceTo(index)
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return

        // Process any pending revive interactions for this entity
        val pendingReviverUUID = PlayerInteractListener.pendingRevives.remove(ref)
        if (pendingReviverUUID != null) {
            println("[HyDowned] Processing pending revive from: $pendingReviverUUID")

            // Add reviver to the set
            if (downedComponent.reviverPlayerIds.add(pendingReviverUUID)) {
                println("[HyDowned] ✓ Added reviver: $pendingReviverUUID")

                // If this is the first reviver, initialize the revive timer
                if (downedComponent.reviverPlayerIds.size == 1) {
                    downedComponent.reviveTimeRemaining = config.reviveTimerSeconds.toDouble()
                    println("[HyDowned] ✓ Initialized revive timer: ${config.reviveTimerSeconds}s")
                } else {
                    println("[HyDowned] ✓ Multiple revivers now: ${downedComponent.reviverPlayerIds.size}")
                }

                // Send feedback to downed player
                val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())
                playerComponent?.sendMessage(Message.raw("A PLAYER IS REVIVING YOU!"))
            } else {
                println("[HyDowned] Reviver already in set")
            }
        }

        // Get player component for sending messages
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())

        // Decrement timer (dt is in seconds, approximately 1.0)
        downedComponent.downedTimeRemaining -= 1

        val timeRemaining = downedComponent.downedTimeRemaining

        println("[HyDowned] Timer tick: ${timeRemaining}s remaining")

        // Send chat messages at specific intervals
        if (playerComponent != null) {
            when (timeRemaining) {
                // Important milestones
                60 -> playerComponent.sendMessage(Message.raw("DOWNED! 60 seconds until death..."))
                30 -> playerComponent.sendMessage(Message.raw("30 seconds remaining!"))
                10 -> playerComponent.sendMessage(Message.raw("10 SECONDS!"))
                5  -> playerComponent.sendMessage(Message.raw("5!"))
                4  -> playerComponent.sendMessage(Message.raw("4!"))
                3  -> playerComponent.sendMessage(Message.raw("3!"))
                2  -> playerComponent.sendMessage(Message.raw("2!"))
                1  -> playerComponent.sendMessage(Message.raw("1!"))
                // Every 10 seconds for longer timers
                else -> {
                    if (timeRemaining > 60 && timeRemaining % 30 == 0) {
                        playerComponent.sendMessage(Message.raw("DOWNED! ${timeRemaining} seconds remaining..."))
                    }
                }
            }
        }

        // Check if revivers are still present
        if (downedComponent.reviverPlayerIds.isNotEmpty()) {
            // TODO: Verify revivers are still nearby and online
            // For now, just process the revive timer

            // Calculate revive speed based on reviver count
            val reviverCount = downedComponent.reviverPlayerIds.size
            val speedMultiplier = if (config.multipleReviversMode == "SPEEDUP") {
                1.0 + ((reviverCount - 1) * config.reviveSpeedupPerPlayer)
            } else {
                1.0
            }

            // Decrement revive timer
            downedComponent.reviveTimeRemaining -= speedMultiplier

            println("[HyDowned] Revivers: ${reviverCount}, Speed: ${speedMultiplier}x, Remaining: ${downedComponent.reviveTimeRemaining}s")

            // Check if revive complete
            if (downedComponent.reviveTimeRemaining <= 0) {
                println("[HyDowned] ============================================")
                println("[HyDowned] Revive complete!")
                println("[HyDowned] ============================================")

                // Remove downed component
                commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

                // Clear downed state tracking
                DownedStateTracker.setNotDowned(ref)

                // Restore health
                val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
                if (entityStatMap != null) {
                    val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
                    if (healthStat != null) {
                        val restoreAmount = healthStat.max * config.reviveHealthPercent.toFloat()
                        entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), restoreAmount)
                        println("[HyDowned] Restored health to ${restoreAmount} (${config.reviveHealthPercent * 100}%)")
                    }
                }

                // Send success messages
                playerComponent?.sendMessage(Message.raw("REVIVED! YOU'RE BACK! ✚✚✚"))

                // TODO: Notify revivers of success
                println("[HyDowned] ✓ Player revived successfully")
                return // Exit early - player is revived
            }
        }

        // Check if timer expired
        if (timeRemaining <= 0) {
            println("[HyDowned] ============================================")
            println("[HyDowned] Downed timer expired!")
            println("[HyDowned] Executing death...")
            println("[HyDowned] ============================================")

            // Send final message
            playerComponent?.sendMessage(Message.raw("Time's up! Executing death..."))

            // Remove downed component first
            commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

            // Clear downed state tracking
            DownedStateTracker.setNotDowned(ref)

            // Create fatal damage with original cause (if available)
            // Use the damage cause index to avoid ambiguity
            val damageCauseIndex = if (downedComponent.originalDamageCause != null) {
                // Get the index from the asset map
                downedComponent.originalDamage?.damageCauseIndex ?: 0
            } else {
                0 // Default fallback
            }

            val killDamage = Damage(
                downedComponent.originalDamage?.source ?: Damage.NULL_SOURCE,
                damageCauseIndex,
                999999.0f // Massive damage to ensure death
            )

            // Execute death - this will add DeathComponent and trigger normal death flow
            DamageSystems.executeDamage(ref, commandBuffer, killDamage)

            println("[HyDowned] ✓ Death executed, normal respawn flow will proceed")
        }
    }

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
        // Run in parallel for better performance
        return true
    }
}
