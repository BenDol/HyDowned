package com.hydowned.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.config.ModConfig
import com.hydowned.ModPlugin
import com.hydowned.extension.sendModMessage
import java.util.concurrent.ConcurrentHashMap
import com.hydowned.logging.Log
import com.hypixel.hytale.protocol.GameMode


/**
 * Command that allows downed players to immediately give up and die.
 *
 * Usage: /giveup
 *
 * This provides a way for players to:
 * - Skip the downed timer if they don't want to wait for revival
 * - Avoid having to log out to respawn
 * - Quickly respawn if they're in a bad situation
 *
 * Uses a pending queue that DownedTimerSystem processes (same pattern as revive interactions)
 */
class GiveUpCommand(
    private val config: ModConfig
) : AbstractPlayerCommand("giveup", "hydowned.commands.giveup.desc") {

    init {
        // Allow all adventure mode players to use this command
        this.setPermissionGroup(GameMode.Adventure)
    }

    companion object {
        // Queue of pending give-up requests (playerRef -> true)
        val pendingGiveUps = ConcurrentHashMap<Ref<EntityStore>, Boolean>()
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World
    ) {
        // Check if player is downed using the new manager-based approach
        val managers = ModPlugin.instance?.managers ?: return
        val modPlayer = managers.playerManager.get(playerRef)

        if (modPlayer == null || !modPlayer.asDownable.isDowned()) {
            playerRef.sendModMessage(Message.translation("hydowned.command.not_knocked_out"))
            return
        }

        Log.finer("GiveUpCommand", "Player used /giveup while downed")

        // Kill the player immediately using the down manager
        managers.downManager.kill(modPlayer.asDownable, "GiveUp")

        Log.finer("GiveUpCommand", "Player gave up and died")

        // Send message
        playerRef.sendModMessage(Message.translation("hydowned.command.giving_up"))
    }
}
