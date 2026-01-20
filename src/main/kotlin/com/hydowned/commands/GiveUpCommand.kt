package com.hydowned.commands

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import java.util.concurrent.ConcurrentHashMap
import com.hydowned.util.Log


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
    private val config: DownedConfig
) : AbstractPlayerCommand("giveup", "hydowned.commands.giveup.desc") {

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
        // Check if player is downed
        val downedComponent = store.getComponent(ref, DownedComponent.getComponentType())
        if (downedComponent == null) {
            playerRef.sendMessage(Message.raw("§cYou are not downed!"))
            return
        }

        println("[HyDowned] [GiveUpCommand] Player used /giveup while downed")

        // Queue this give-up request for processing by DownedTimerSystem
        // The timer system has access to the ECS commandBuffer and can safely execute death
        pendingGiveUps[ref] = true

        Log.verbose("GiveUpCommand", "Queued give-up in pendingGiveUps map")

        // Send message immediately
        playerRef.sendMessage(Message.raw("§eGiving up... You will respawn shortly."))
    }
}
