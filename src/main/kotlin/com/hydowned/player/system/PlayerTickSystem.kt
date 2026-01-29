package com.hydowned.player.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.EntityUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.player.aspect.PlayerDownable
import com.hydowned.extension.getDistance
import com.hydowned.extension.getNearestDownable
import com.hydowned.logging.Log
import com.hydowned.manager.Managers
import com.hydowned.player.aspect.ModPlayer
import com.hydowned.player.aspect.PlayerReviver

/**
 * Player tick system.
 *
 * Handles:
 * - HUD display for nearby downed players
 * - Give up mechanics
 * - Distance checks for active revives
 */
class PlayerTickSystem(val managers: Managers) : EntityTickingSystem<EntityStore>() {
    private val query: Query<EntityStore> = Query.and(Player.getComponentType())

    override fun getQuery(): Query<EntityStore>? {
        return query
    }

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val holder = EntityUtils.toHolder(index, archetypeChunk)
        val player = holder.getComponent(Player.getComponentType())
        val playerRef = holder.getComponent(PlayerRef.getComponentType())

        if (player == null || playerRef == null || !playerRef.isValid) {
            return
        }

        val modPlayer = managers.playerManager.get(player) ?: return
        val downable = modPlayer.asDownable
        val reviver = modPlayer.asReviver

        // Handle downed player logic
        if (downable.isDowned()) {
            handleDownedPlayer(dt, player, playerRef, modPlayer, downable)
            return
        }

        // Handle reviving player logic
        if (managers.reviveManager.isReviving(reviver)) {
            handleRevivingPlayer(dt, player, playerRef, modPlayer, reviver)
            return
        }

        // Show HUD for nearby downed players
        var isShowing = false
        if (managers.downManager.getAll().isNotEmpty()) {
            val downableNearest = playerRef.getNearestDownable()
            if (downableNearest != null && playerRef.getDistance(downableNearest.playerRef) <= 3.0) {
                val targetRevivePlayer = managers.playerManager.get(downableNearest.player)
                if (targetRevivePlayer != null) {
                    managers.hudManager.showHud(targetRevivePlayer, modPlayer, true)
                    modPlayer.isShowingHelpMessage = true
                    isShowing = true
                }
            }
        }

        if (!isShowing && modPlayer.isShowingHelpMessage) {
            managers.hudManager.showHud(modPlayer, modPlayer, false)
            modPlayer.isShowingHelpMessage = false
        }
    }

    fun handleDownedPlayer(
        dt: Float,
        player: Player,
        playerRef: PlayerRef,
        modPlayer: ModPlayer,
        downable: PlayerDownable
    ) {
        // Handle give up
        if (downable.giveUpTicks > 0) {
            downable.giveUpTicks--
        } else if (downable.giveUpTicks == 0) {
            downable.giveUpTicks = -1
            managers.downManager.kill(downable, "gave up")
        }

        // Show HUD for downed player
        managers.hudManager.showHud(modPlayer, modPlayer, true)
    }

    fun handleRevivingPlayer(
        dt: Float,
        player: Player,
        playerRef: PlayerRef,
        modPlayer: ModPlayer,
        reviver: PlayerReviver
    ) {
        val target = managers.reviveManager.getReviveTarget(reviver)
        if (target != null) {
            val playerDownable = target as? PlayerDownable
            if (playerDownable != null) {
                val reviveDistance = managers.config.revive.maxRange
                val distance = playerRef.getDistance(playerDownable.playerRef)

                if (distance > reviveDistance) {
                    managers.reviveManager.cancel(reviver)
                    Log.finer("PlayerTickSystem", "${player.displayName} moved too far, revive canceled")
                }
            }

            // Show target HUD
            val targetDownable = target as? PlayerDownable
            if (targetDownable != null) {
                val targetRevivePlayer = managers.playerManager.get(targetDownable.player)
                if (targetRevivePlayer != null) {
                    managers.hudManager.showHud(targetRevivePlayer, modPlayer, true)
                }
            }
        }
    }
}
