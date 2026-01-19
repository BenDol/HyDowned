package com.hydowned.state

import com.hydowned.config.DownedConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownedStateManager(private val config: DownedConfig) {
    private val downedPlayers = ConcurrentHashMap<UUID, DownedState>()

    fun setDowned(playerId: UUID, location: Any? = null): DownedState {
        val state = DownedState(
            playerId = playerId,
            downedTimeRemaining = config.downedTimerSeconds,
            downedLocation = location
        )
        downedPlayers[playerId] = state
        return state
    }

    fun addReviver(downedPlayerId: UUID, reviverId: UUID): Boolean {
        val state = downedPlayers[downedPlayerId] ?: return false

        // Check revive mode
        if (!config.isSpeedupMode && state.hasRevivers) {
            // FIRST_ONLY mode - block if someone is already reviving
            return false
        }

        state.addReviver(reviverId)

        // Initialize revive timer if this is the first reviver
        if (state.reviveTimeRemaining == Double.MAX_VALUE) {
            state.reviveTimeRemaining = config.reviveTimerSeconds.toDouble()
        }

        return true
    }

    fun removeReviver(downedPlayerId: UUID, reviverId: UUID) {
        downedPlayers[downedPlayerId]?.removeReviver(reviverId)
    }

    fun cancelAllRevivers(downedPlayerId: UUID) {
        downedPlayers[downedPlayerId]?.clearRevivers()
    }

    fun completeRevive(playerId: UUID): Boolean {
        return downedPlayers.remove(playerId) != null
    }

    fun completeDeath(playerId: UUID): DownedState? {
        return downedPlayers.remove(playerId)
    }

    fun isDowned(playerId: UUID): Boolean {
        return downedPlayers.containsKey(playerId)
    }

    fun isReviving(playerId: UUID): Boolean {
        return downedPlayers[playerId]?.hasRevivers ?: false
    }

    fun getReviverCount(playerId: UUID): Int {
        return downedPlayers[playerId]?.reviverCount ?: 0
    }

    fun getState(playerId: UUID): DownedState? {
        return downedPlayers[playerId]
    }

    fun getAllDownedStates(): Map<UUID, DownedState> {
        return downedPlayers.toMap()
    }

    fun isPlayerRevivingSomeone(playerId: UUID): Pair<UUID, DownedState>? {
        return downedPlayers.entries.firstOrNull { (_, state) ->
            state.isReviver(playerId)
        }?.let { it.key to it.value }
    }

    fun cleanup() {
        downedPlayers.clear()
    }
}
