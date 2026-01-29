package com.hydowned.component

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * Component that stores the remaining time for a downed player.
 *
 * This component persists across server restarts, allowing players
 * to resume their downed state if they log back in.
 */
class DownedComponent(
    var time: Int = 0
) : Component<EntityStore> {

    companion object {
        @JvmField
        val CODEC: BuilderCodec<DownedComponent> = BuilderCodec.builder(DownedComponent::class.java) { DownedComponent() }
            .append(
                KeyedCodec("TimeLeft", Codec.INTEGER),
                { state: DownedComponent, count: Int -> state.time = count },
                { state: DownedComponent -> state.time }
            )
            .add()
            .build()

        private var componentType: ComponentType<EntityStore, DownedComponent>? = null

        fun getComponentType(): ComponentType<EntityStore, DownedComponent> {
            return componentType ?: throw IllegalStateException("DownedComponent type not registered")
        }

        fun setComponentType(type: ComponentType<EntityStore, DownedComponent>) {
            componentType = type
        }
    }

    override fun clone(): Component<EntityStore> {
        return DownedComponent(time)
    }
}



