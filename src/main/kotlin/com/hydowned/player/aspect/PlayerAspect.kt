package com.hydowned.player.aspect

import com.hydowned.ModPlugin
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hydowned.aspect.Aspect
import com.hydowned.manager.Managers
import com.hydowned.extension.getDistance
import com.hydowned.extension.getNearestDownable
import com.hypixel.hytale.server.core.universe.PlayerRef

open class PlayerAspect(
    val player: Player,
    val playerRef: PlayerRef
) : Aspect {
    override fun getDisplayName(): String = player.displayName ?: "Unknown Player"

    fun getDistance(target: PlayerAspect): Double {
        return playerRef.getDistance(target.playerRef)
    }

    fun getDistance(targetRef: PlayerRef): Double {
        return playerRef.getDistance(targetRef)
    }

    fun getNearestDownable(
        managers: Managers = ModPlugin.instance!!.managers
    ): PlayerDownable? {
        return playerRef.getNearestDownable(managers)
    }
}
