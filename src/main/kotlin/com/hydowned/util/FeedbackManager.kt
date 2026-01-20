package com.hydowned.util

import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


class FeedbackManager(private val config: DownedConfig) {

    fun sendDownedFeedback(player: Any, timeRemaining: Int) {
        val minutes = timeRemaining / 60
        val seconds = timeRemaining % 60
        val timeStr = String.format("%d:%02d", minutes, seconds)

        if (config.enableActionBar) {
            // TODO: Implement with actual Hytale API
            // Example pseudocode using KTale:
            // player.sendActionBar("⚠ Downed: $timeStr remaining")
            Log.debug("Feedback", "Action Bar -> Player: ⚠ Downed: $timeStr remaining")
        }

        if (config.enableParticles) {
            // TODO: Implement with actual Hytale particle API
            // Example: spawn red particles around player
            Log.debug("Feedback", "Particles -> Red particles around player")
        }

        if (config.enableSounds) {
            // TODO: Implement with actual Hytale sound API
            // Example: play heartbeat or hurt sound
            Log.debug("Feedback", "Sound -> Heartbeat sound for player")
        }
    }

    fun sendReviveFeedback(downedPlayer: Any, revivers: Set<Any>, progress: Double) {
        val percentage = (progress * 100).toInt()

        if (config.enableActionBar) {
            // TODO: Implement with actual Hytale API
            // For downed player
            // downedPlayer.sendActionBar("✚ Being revived... $percentage%")
            Log.debug("Feedback", "Action Bar -> Downed Player: ✚ Being revived... $percentage%")

            // For each reviver
            revivers.forEach { reviver ->
                // reviver.sendActionBar("✚ Reviving [PlayerName]... $percentage%")
                Log.debug("Feedback", "Action Bar -> Reviver: ✚ Reviving... $percentage%")
            }
        }

        if (config.enableParticles) {
            // TODO: Implement with actual Hytale particle API
            // Example: spawn green/healing particles
            Log.debug("Feedback", "Particles -> Green healing particles")
        }

        if (config.enableSounds) {
            // TODO: Implement with actual Hytale sound API
            // Example: play healing/magical sound
            Log.debug("Feedback", "Sound -> Healing sound")
        }
    }

    fun sendReviveCompleteFeedback(player: Any) {
        // TODO: Implement with actual Hytale API
        // Example using KTale:
        // player.send("<green>You have been revived!</green>")
        Log.debug("Feedback", "Chat -> Player: §aYou have been revived!")

        if (config.enableParticles) {
            // TODO: Implement particle burst
            Log.debug("Feedback", "Particles -> Burst of healing particles")
        }

        if (config.enableSounds) {
            // TODO: Implement success sound
            Log.debug("Feedback", "Sound -> Success sound")
        }
    }

    fun sendReviveCancelledFeedback(reviver: Any, reason: String) {
        // TODO: Implement with actual Hytale API
        // Example using KTale:
        // reviver.send("<red>Revive cancelled: $reason</red>")
        Log.debug("Feedback", "Chat -> Reviver: §cRevive cancelled: $reason")
    }

    fun sendDownedMessage(player: Any, timeSeconds: Int) {
        // TODO: Implement with actual Hytale API
        // Example using KTale:
        // player.send("<yellow>You are downed! Wait for revival or respawn in $timeSeconds seconds</yellow>")
        Log.debug("Feedback", "Chat -> Player: §eYou are downed! Wait for revival or respawn in $timeSeconds seconds")
    }

    fun sendReviveStartedFeedback(reviver: Any, downedPlayerName: String) {
        // TODO: Implement with actual Hytale API
        // Example using KTale:
        // reviver.send("<green>Reviving $downedPlayerName...</green>")
        Log.debug("Feedback", "Chat -> Reviver: §aReviving $downedPlayerName...")
    }
}
