package com.hydowned.state

import java.util.UUID

data class DownedState(
    val playerId: UUID,
    var downedTimeRemaining: Int,
    var reviverPlayerIds: MutableSet<UUID> = mutableSetOf(),
    var reviveTimeRemaining: Double = 0.0,
    val downedAt: Long = System.currentTimeMillis(),
    var downedLocation: Any? = null  // Will be Location type from Hytale API
) {
    val hasRevivers: Boolean
        get() = reviverPlayerIds.isNotEmpty()

    val reviverCount: Int
        get() = reviverPlayerIds.size

    fun addReviver(reviverId: UUID) {
        if (reviverPlayerIds.add(reviverId) && reviveTimeRemaining == 0.0) {
            // Start revive timer when first reviver is added
            reviveTimeRemaining = Double.MAX_VALUE  // Will be set to actual value by manager
        }
    }

    fun removeReviver(reviverId: UUID) {
        reviverPlayerIds.remove(reviverId)
        if (reviverPlayerIds.isEmpty()) {
            reviveTimeRemaining = 0.0
        }
    }

    fun clearRevivers() {
        reviverPlayerIds.clear()
        reviveTimeRemaining = 0.0
    }

    fun isReviver(playerId: UUID): Boolean {
        return reviverPlayerIds.contains(playerId)
    }
}
