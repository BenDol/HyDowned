package com.hydowned.manager

import com.hydowned.config.ModConfig
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hydowned.player.aspect.ModPlayer
import com.hydowned.logging.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages ModPlayer wrappers for all online players.
 *
 * Creates ModPlayer instances when players join and cleans up when they leave.
 */
class PlayerManager(private val config: ModConfig) {
    private val players = ConcurrentHashMap<PlayerRef, ModPlayer>()

    fun add(player: Player, playerRef: PlayerRef) {
        val modPlayer = ModPlayer(player, playerRef)
        players[playerRef] = modPlayer
        Log.finer("PlayerManager", "Player joined: ${player.displayName}")
    }

    fun remove(modPlayer: ModPlayer) {
        players.remove(modPlayer.playerRef)
        Log.finer("PlayerManager", "Player left: ${modPlayer.player.displayName}")
    }

    fun get(player: Player): ModPlayer? {
        return players.values.firstOrNull { it.player == player }
    }

    fun get(playerRef: PlayerRef): ModPlayer? {
        return players[playerRef]
    }

    fun getAll(): Collection<ModPlayer> {
        return players.values
    }
}
