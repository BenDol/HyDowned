package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Clears all active entity effects (buffs, debuffs, status effects) when a player enters downed state.
 *
 * Uses the same approach as DeathSystems.ClearEntityEffects.
 * This ensures downed players don't keep:
 * - Regeneration effects
 * - Speed boosts
 * - Damage buffs
 * - Debuffs like poison or slowness
 * - Any other active effects
 *
 * Runs when DownedComponent is added to the entity.
 */
class DownedClearEffectsSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        EffectControllerComponent.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val effectController = commandBuffer.getComponent(ref, EffectControllerComponent.getComponentType())
        if (effectController != null) {
            effectController.clearEffects(ref, commandBuffer)
            println("[HyDowned] Cleared all entity effects for downed player")
        }
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - only clear effects when first downed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - effects naturally restore when revived
    }
}
