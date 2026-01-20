package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Plays the death animation when a player becomes downed.
 *
 * This triggers ONCE when DownedComponent is added.
 * The packet interceptor prevents other animations from overriding it.
 */
class DownedAnimationSystem(
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
        println("[HyDowned] [Animation] Playing death animation for downed player")

        // Use AnimationUtils to play the death animation
        // sendToSelf = true so the downed player sees their own animation
        AnimationUtils.playAnimation(
            ref,
            AnimationSlot.Movement,
            "Death",
            true, // sendToSelf
            commandBuffer
        )

        println("[HyDowned] [Animation] ✓ Death animation played via AnimationUtils")
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
        println("[HyDowned] [Animation] Player revived, clearing death animation")

        // Use AnimationUtils to stop the animation
        // sendToSelf = true so the downed player's animation is cleared
        AnimationUtils.stopAnimation(
            ref,
            AnimationSlot.Movement,
            true, // sendToSelf
            commandBuffer
        )

        println("[HyDowned] [Animation] ✓ Death animation stopped via AnimationUtils")
    }
}
