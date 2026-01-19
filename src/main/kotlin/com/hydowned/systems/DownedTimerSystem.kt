package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import javax.annotation.Nonnull

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

    @Nonnull
    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        @Nonnull archetypeChunk: ArchetypeChunk<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull commandBuffer: CommandBuffer<EntityStore>
    ) {
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return

        // Decrement timer (dt is in seconds, approximately 1.0)
        downedComponent.downedTimeRemaining -= 1

        println("[HyDowned] Timer tick: ${downedComponent.downedTimeRemaining}s remaining")

        // Check if revivers are still present
        if (downedComponent.reviverPlayerIds.isNotEmpty()) {
            // TODO: Verify revivers are still nearby and valid
            // For now, just log
            println("[HyDowned]   Revivers present: ${downedComponent.reviverPlayerIds.size}")

            // TODO: Decrement revive timer
            // TODO: Check if revive complete
        }

        // Check if timer expired
        if (downedComponent.downedTimeRemaining <= 0) {
            println("[HyDowned] ============================================")
            println("[HyDowned] Downed timer expired!")
            println("[HyDowned] Executing death...")
            println("[HyDowned] ============================================")

            val ref = archetypeChunk.getReferenceTo(index)

            // Remove downed component first
            commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

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
