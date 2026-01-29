package com.hydowned.extension

import com.hydowned.ModPlugin
import com.hydowned.manager.Managers
import com.hydowned.player.aspect.PlayerDownable
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import kotlin.math.sqrt

fun PlayerRef.getDistance(targetRef: PlayerRef): Double {
    val reference1 = this.reference ?: return Double.MAX_VALUE
    val reference2 = targetRef.reference ?: return Double.MAX_VALUE

    val pos1 = reference1.store.getComponent(
        reference1,
        TransformComponent.getComponentType()
    )

    val pos2 = reference2.store.getComponent(
        reference2,
        TransformComponent.getComponentType()
    )

    if (pos1 == null || pos2 == null) {
        return Double.MAX_VALUE
    }

    val dx = pos1.position.x - pos2.position.x
    val dy = pos1.position.y - pos2.position.y
    val dz = pos1.position.z - pos2.position.z

    return sqrt(dx * dx + dy * dy + dz * dz)
}

fun PlayerRef.getNearestDownable(
    managers: Managers = ModPlugin.instance!!.managers
): PlayerDownable? {
    var nearest: PlayerDownable? = null
    var nearestDistance = Double.MAX_VALUE

    for (downable in managers.downManager.getAll()) {
        if (downable is PlayerDownable) {
            val distance = this.getDistance(downable.playerRef)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = downable
            }
        }
    }

    return nearest
}