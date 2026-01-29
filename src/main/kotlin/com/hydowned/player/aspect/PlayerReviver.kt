package com.hydowned.player.aspect

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hydowned.ModPlugin
import com.hydowned.aspect.Downable
import com.hydowned.aspect.Reviver
import com.hydowned.extension.getDistance
import com.hydowned.logging.Log

/**
 * Implementation of Reviver for players.
 *
 * Handles player revive logic including distance checks and duration.
 */
class PlayerReviver(
    player: Player,
    playerRef: PlayerRef
) : PlayerAspect(player, playerRef), Reviver {

    override fun getDisplayName(): String {
        return player.displayName ?: "Unknown"
    }

    override fun canRevive(target: Downable): Boolean {
        if (!target.isDowned()) {
            return false
        }

        // Check if target can be revived by this reviver
        if (!target.canBeRevivedBy(this)) {
            return false
        }

        // Check distance (if target is PlayerDownable)
        if (target is PlayerDownable) {
            val distance = playerRef.getDistance(target.playerRef)
            val maxDistance = ModPlugin.instance!!.config.revive.maxRange
            if (distance > maxDistance) {
                return false
            }
        }

        return true
    }

    override fun getReviveDuration(): Double {
        val config = ModPlugin.instance!!.config
        return config.revive.timerSeconds.toDouble() * 20.0
    }

    override fun startRevive(target: Downable) {
        Log.finer("PlayerReviver", "${getDisplayName()} started reviving ${target.getDisplayName()}")
        // Additional start logic can be added here
    }

    override fun cancelRevive() {
        Log.finer("PlayerReviver", "${getDisplayName()} canceled revive")
        // Additional cancel logic can be added here
    }

    override fun finishRevive() {
        Log.finer("PlayerReviver", "${getDisplayName()} finished revive")
        // Additional finish logic can be added here
    }
}
