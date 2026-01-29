package com.hydowned.player.aspect

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef

/**
 * Wrapper class that combines all player-related functionality.
 *
 * Each online player has one ModPlayer instance that contains:
 * - PlayerDownable: ability to be downed
 * - PlayerReviver: ability to revive others
 * - PlayerAggressor: ability to down others
 */
class ModPlayer(
    player: Player,
    playerRef: PlayerRef
) : PlayerAspect(player, playerRef) {
    val asDownable: PlayerDownable = PlayerDownable(player, playerRef)
    val asReviver: PlayerReviver = PlayerReviver(player, playerRef)
    val asAggressor: PlayerAspect = PlayerAspect(player, playerRef)

    var isShowingHelpMessage: Boolean = false

    override fun toString(): String {
        return "ModPlayer(${player.displayName})"
    }
}
