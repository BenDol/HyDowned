package com.hydowned.manager

import com.hydowned.ModPlugin
import com.hydowned.config.ModConfig
import com.hydowned.aspect.Aspect
import com.hydowned.aspect.Downable
import com.hydowned.component.DownedComponent
import com.hydowned.player.aspect.PlayerDownable
import com.hydowned.logging.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Manages downed player states and timers.
 *
 * Uses a simple HashMap to track remaining time for each downed player.
 * Ticks every game tick to decrement timers and handle death/revive.
 */
class DownManager(private val config: ModConfig) {
    private val downed = ConcurrentHashMap<Downable, Double>()

    fun down(target: Downable, aggressor: Aspect): Boolean {
        if (!isDowned(target)) {
            target.onDown(aggressor)
            downed[target] = target.getDownDuration()
            return true
        }

        return false
    }

    fun revive(target: Downable): Boolean {
        if (isDowned(target)) {
            target.onRevived()
            downed.remove(target)
            return true
        }

        return false
    }

    fun kill(target: Downable, reason: String): Boolean {
        if (isDowned(target)) {
            Log.finer("DownManager", "Killing downed player: $reason")
            target.onDeath()
            downed.remove(target)
            return true
        }

        return false
    }

    fun onDeath(target: Downable): Boolean {
        if (isDowned(target)) {
            target.onCancelDown()
            downed.remove(target)
            return true
        }

        return false
    }

    fun isDowned(target: Downable): Boolean {
        return downed.containsKey(target)
    }

    fun getTimeLeft(target: Downable): Double {
        return downed.getOrDefault(target, 0.0)
    }

    fun tick() {
        if (downed.isEmpty()) {
            return
        }

        val managers = ModPlugin.instance!!.managers;

        // Process all downed players
        for (downable in downed.keys.toTypedArray()) {
            // Check if player is still valid
            if (downable is PlayerDownable && !downable.playerRef.isValid) {
                downed.remove(downable)
                continue
            }

            // Skip if being revived
            if (managers.reviveManager.isBeingRevived(downable)) {
                continue
            }

            // Decrement time
            val time = downed[downable]!! - 1.0
            val oldSec = floor((time + 1.0) / 20.0).toInt()
            val newSec = floor(time / 20.0).toInt()

            // Save time to component when second changes
            if (oldSec != newSec && downable is PlayerDownable) {
                storePlayerTimeLeft(downable, time.toInt())
            }

            // Tick the downable
            downable.tick()

            // Check if time expired
            if (time <= 0.0) {
                val deathOnTimeout = config.downed.deathOnTimeout
                if (deathOnTimeout) {
                    kill(downable, "Timeout: $time")
                } else {
                    revive(downable)
                }
                continue
            }

            downed[downable] = time
        }
    }

    fun getAll(): Collection<Downable> {
        return downed.keys
    }

    fun setTime(downable: PlayerDownable, time: Int) {
        downed[downable] = time.toDouble()
        downable.setDownedEffect(time.toFloat(), config.downed.applySlow,
            "setTime: $time")
    }

    fun storePlayerTimeLeft(downable: PlayerDownable, time: Int) {
        val player = downable.player
        player.world?.execute {
            val reference = player.reference ?: return@execute
            val store = reference.store
            val downedComponent = store.getComponent(
                reference,
                DownedComponent.getComponentType()
            ) ?: return@execute

            downedComponent.time = time
        }
    }

    fun remove(downable: PlayerDownable) {
        downed.remove(downable)
    }
}
