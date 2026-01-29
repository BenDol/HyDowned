package com.hydowned.manager

import com.hydowned.ModPlugin
import com.hydowned.config.ModConfig
import com.hydowned.aspect.Downable
import com.hydowned.aspect.Reviver
import com.hydowned.player.aspect.PlayerReviver
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active revive attempts.
 *
 * Tracks who is reviving whom and handles revive progression/completion.
 */
class ReviveManager(private val config: ModConfig) {
    private val byReviver = ConcurrentHashMap<Reviver, Downable>()
    private val byTarget = ConcurrentHashMap<Downable, Reviver>()
    private val time = ConcurrentHashMap<Reviver, Double>()

    fun start(reviver: Reviver, target: Downable): Boolean {
        if (isReviving(reviver)) {
            return false
        }
        if (!reviver.canRevive(target)) {
            return false
        }
        if (isBeingRevived(target)) {
            return false
        }

        byReviver[reviver] = target
        byTarget[target] = reviver
        time[reviver] = 0.0
        reviver.startRevive(target)
        return true
    }

    fun cancel(reviver: Reviver): Boolean {
        if (!isReviving(reviver)) {
            return false
        }

        val target = byReviver.remove(reviver)
        if (target != null) {
            byTarget.remove(target)
        }
        reviver.cancelRevive()
        time.remove(reviver)
        hideReviverHud(reviver)
        return true
    }

    fun finish(reviver: Reviver): Boolean {
        if (!isReviving(reviver)) {
            return false
        }

        val target = byReviver.remove(reviver)
        if (target != null) {
            target.onRevived()
            byTarget.remove(target)
        }

        ModPlugin.instance!!.managers.downManager.revive(target!!)
        reviver.finishRevive()
        time.remove(reviver)
        hideReviverHud(reviver)
        return true
    }

    private fun hideReviverHud(reviver: Reviver) {
        if (reviver is PlayerReviver) {
            val managers = ModPlugin.instance!!.managers
            val modPlayer = managers.playerManager.get(reviver.player) ?: return
            managers.hudManager.showHud(modPlayer, modPlayer, false)
        }
    }

    fun isReviving(reviver: Reviver): Boolean {
        return byReviver.containsKey(reviver)
    }

    fun isBeingRevived(target: Downable): Boolean {
        return byTarget.containsKey(target)
    }

    fun getProgress(reviver: Reviver): Double {
        val t = time.getOrDefault(reviver, 0.0)
        val required = reviver.getReviveDuration()
        return if (required <= 0.0) 0.0 else t / required
    }

    fun tick() {
        if (byReviver.isEmpty()) {
            return
        }

        for (reviver in byReviver.keys.toTypedArray()) {
            val t = time[reviver]!! + 1.0
            val required = reviver.getReviveDuration()

            if (t >= required) {
                finish(reviver)
                continue
            }

            time[reviver] = t
        }
    }

    fun getReviverOf(target: Downable): Reviver? {
        return byTarget[target]
    }

    fun getReviveTarget(reviver: Reviver): Downable? {
        return byReviver[reviver]
    }
}
