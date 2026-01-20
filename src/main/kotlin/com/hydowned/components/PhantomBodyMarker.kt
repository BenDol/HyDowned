package com.hydowned.components

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.Equipment
import com.hypixel.hytale.protocol.PlayerSkin
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.HyDownedPlugin

/**
 * Marker component for phantom bodies that need their death animation played, equipment set, and skin applied.
 * This is used to defer animation/equipment/skin until after the entity is fully spawned and visible.
 */
class PhantomBodyMarker(
    var playerRef: Ref<EntityStore>? = null,  // Reference to the downed player
    var equipment: Equipment? = null,          // Equipment to display on phantom body
    var playerSkin: PlayerSkin? = null         // Cosmetic skin/outfit to display on phantom body
) : Component<EntityStore> {

    companion object {
        private var componentType: ComponentType<EntityStore, PhantomBodyMarker>? = null

        fun getComponentType(): ComponentType<EntityStore, PhantomBodyMarker> {
            if (componentType == null) {
                componentType = HyDownedPlugin.instance!!.getPhantomBodyMarkerComponentType()
            }
            return componentType!!
        }
    }

    override fun clone(): Component<EntityStore> {
        return PhantomBodyMarker(playerRef, equipment?.clone(), playerSkin)
    }
}
