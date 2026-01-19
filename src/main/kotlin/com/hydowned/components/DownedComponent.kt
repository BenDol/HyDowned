package com.hydowned.components

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.HyDownedPlugin

/**
 * Component that marks an entity as being in a "downed" state
 * This replaces death - player stays alive at 1 HP and can be revived
 */
class DownedComponent(
    var downedTimeRemaining: Int,
    var reviverPlayerIds: MutableSet<String> = mutableSetOf(),
    var reviveTimeRemaining: Double = 0.0,
    val downedAt: Long = System.currentTimeMillis(),
    var downedLocation: Vector3d? = null,
    var originalDamageCause: DamageCause? = null,
    var originalDamage: Damage? = null
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
            originalDamage
        )
    }
}
