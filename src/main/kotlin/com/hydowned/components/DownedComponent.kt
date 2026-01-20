package com.hydowned.components

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.HyDownedPlugin

/**
 * Component that marks an entity as being in a "downed" state
 * This replaces death - player stays alive at 1 HP and can be revived
 *
 * Phantom body approach: Spawn fake NPC body at downed location, let player move within 10 block radius
 */
class DownedComponent(
    var downedTimeRemaining: Int,
    var reviverPlayerIds: MutableSet<String> = mutableSetOf(),
    var reviveTimeRemaining: Double = 0.0,
    val downedAt: Long = System.currentTimeMillis(),
    var downedLocation: Vector3d? = null,
    var originalDamageCause: DamageCause? = null,
    var originalDamage: Damage? = null,
    var phantomBodyRef: com.hypixel.hytale.component.Ref<EntityStore>? = null, // Reference to phantom body NPC
    var equipmentData: com.hypixel.hytale.protocol.Equipment? = null, // Player's equipment for phantom body display
    var originalScale: Float = 1.0f, // Store original scale for restoration (SCALE mode)
    var originalDisplayName: DisplayNameComponent? = null, // Store original display name for restoration
    var wasVisibleBefore: Boolean = true, // Store original visibility state (INVISIBLE mode)
    var hadCollisionEnabled: Boolean = true // Store original collision state
) : Component<EntityStore> {

    companion object {
        fun getComponentType(): ComponentType<EntityStore, DownedComponent> {
            return HyDownedPlugin.instance!!.getDownedComponentType()
        }
    }

    override fun clone(): Component<EntityStore> {
        return DownedComponent(
            downedTimeRemaining,
            reviverPlayerIds.toMutableSet(),
            reviveTimeRemaining,
            downedAt,
            downedLocation?.clone(),
            originalDamageCause,
            originalDamage,
            phantomBodyRef, // Shallow copy - refs are safe to share
            equipmentData?.clone(), // Clone equipment data
            originalScale, // Copy original scale
            originalDisplayName?.clone() as? DisplayNameComponent, // Clone display name
            wasVisibleBefore, // Copy visibility state
            hadCollisionEnabled // Copy collision state
        )
    }
}
