package com.hydowned.util

import com.hydowned.util.Log

class MovementManager {

    // Cache for storing original speeds to restore later
    private val originalSpeeds = mutableMapOf<Any, Double>()

    fun applyDownedSpeed(player: Any, multiplier: Double) {
        // TODO: Implement with actual Hytale player API
        // This is a placeholder that will need to be updated with the actual API
        // Example pseudocode using KTale:
        // val stats = player.entityStatMapOrNull()
        // if (stats != null) {
        //     val currentSpeed = stats.get(EntityStatType.MOVEMENT_SPEED)
        //     originalSpeeds[player] = currentSpeed
        //     stats.set(EntityStatType.MOVEMENT_SPEED, currentSpeed * multiplier)
        // }

        Log.verbose("Movement", "Applying downed speed multiplier: $multiplier for player")
    }

    fun restoreNormalSpeed(player: Any) {
        // TODO: Implement with actual Hytale player API
        // Example pseudocode:
        // val originalSpeed = originalSpeeds.remove(player)
        // if (originalSpeed != null) {
        //     val stats = player.entityStatMapOrNull()
        //     stats?.set(EntityStatType.MOVEMENT_SPEED, originalSpeed)
        // }

        Log.verbose("Movement", "Restoring normal speed for player")
    }

    fun cleanup() {
        originalSpeeds.clear()
    }
}
