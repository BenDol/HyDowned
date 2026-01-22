package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.Intangible
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.ComponentUtils
import com.hydowned.util.Log


/**
 * Attempts to make downed players untargetable by mobs.
 *
 * When a player becomes downed:
 * - Adds Intangible component (prevents collision-based detection)
 * - Removes RespondToHit component (prevents knockback, may affect targeting)
 *
 * When a player is revived or dies:
 * - Restores all components to original state
 *
 * NOTE: Hytale's mob AI system is not well documented. These approaches may not
 * fully prevent mob targeting depending on how mob AI is implemented.
 * If mobs still target downed players, this is a limitation of the available API.
 *
 * We cannot use HiddenFromAdventurePlayers as it causes client crashes when
 * combined with the camera system in PLAYER mode.
 *
 * Works in both PLAYER and PHANTOM modes.
 */
class DownedMobAggroSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        Log.finer("MobAggro", "Attempting to prevent mob targeting")

        var approachesApplied = 0

        // Approach 1: Add Intangible component (prevents collision-based detection)
        val hadIntangible = commandBuffer.getComponent(ref, Intangible.getComponentType()) != null
        component.wasTargetable = !hadIntangible // Track original state

        if (ComponentUtils.addComponentIfMissing(ref, commandBuffer, Intangible.getComponentType(), "Intangible", "MobAggro")) {
            approachesApplied++
        }

        // Approach 2: Remove RespondToHit (prevents knockback, may affect AI targeting)
        val hadRespondToHit = commandBuffer.getComponent(ref, RespondToHit.getComponentType()) != null
        if (hadRespondToHit && ComponentUtils.removeComponentSafely(ref, commandBuffer, RespondToHit.getComponentType(), "RespondToHit", "MobAggro")) {
            approachesApplied++
        }

        Log.finer("MobAggro", "Applied $approachesApplied aggro-prevention approaches")
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        Log.finer("MobAggro", "Restoring mob targetability")

        // Restore 1: Remove Intangible component if we added it
        if (component.wasTargetable) { // If they were targetable before, remove Intangible
            ComponentUtils.removeComponentSafely(ref, commandBuffer, Intangible.getComponentType(), "Intangible", "MobAggro")
        }

        // Restore 2: Re-add RespondToHit
        ComponentUtils.ensureComponentSafely(ref, commandBuffer, RespondToHit.getComponentType(), "RespondToHit", "MobAggro")
    }
}
