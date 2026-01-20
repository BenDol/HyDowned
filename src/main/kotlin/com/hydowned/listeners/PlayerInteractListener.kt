package com.hydowned.listeners

import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.config.DownedConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles player interactions to revive downed players
 *
 * When a player right-clicks on a downed player, they start reviving them
 *
 * Uses a pending queue that the DownedTimerSystem processes
 */
class PlayerInteractListener(
    private val config: DownedConfig
) {

    companion object {
        // Queue of pending revive interactions (targetRef -> reviverUUID)
        val pendingRevives = ConcurrentHashMap<Ref<EntityStore>, String>()
    }

    fun onPlayerInteract(event: PlayerInteractEvent) {
        println("[HyDowned] ============================================")
        println("[HyDowned] PlayerInteractEvent triggered!")
        println("[HyDowned]   Event class: ${event.javaClass.name}")
        println("[HyDowned]   Action type: ${event.actionType}")

        // Only handle right-click (Secondary) interactions
        if (event.actionType != InteractionType.Secondary) {
            println("[HyDowned]   ✗ Not secondary interaction - ignoring")
            println("[HyDowned] ============================================")
            return
        }

        println("[HyDowned]   ✓ Secondary interaction (right-click)")

        // Check if target is an entity (not a block)
        val targetRef = event.targetRef
        val targetEntity = event.targetEntity

        println("[HyDowned]   Target Ref: $targetRef")
        println("[HyDowned]   Target Entity: $targetEntity")

        if (targetRef == null) {
            println("[HyDowned]   ✗ No target ref - likely clicked block")
            println("[HyDowned] ============================================")
            return
        }

        if (targetEntity == null) {
            println("[HyDowned]   ✗ No target entity - clicked block or invalid target")
            println("[HyDowned] ============================================")
            return
        }

        // Check if target is a player
        println("[HyDowned]   Target Entity Type: ${targetEntity.javaClass.name}")
        if (targetEntity !is Player) {
            println("[HyDowned]   ✗ Target is not a Player - ignoring")
            println("[HyDowned] ============================================")
            return
        }

        println("[HyDowned]   ✓ Target is a Player!")

        val reviverPlayer = event.player
        println("[HyDowned]   Reviver: ${reviverPlayer.javaClass.name}")

        val reviverUUID = reviverPlayer.uuid.toString()
        println("[HyDowned]   Reviver UUID: $reviverUUID")

        // Queue this revive interaction for processing by DownedTimerSystem
        // The timer system has access to the ECS components and can safely modify them
        pendingRevives[targetRef] = reviverUUID

        println("[HyDowned]   ✓ Queued revive in pendingRevives map")
        println("[HyDowned]   ✓ Map size now: ${pendingRevives.size}")

        // Send immediate feedback
        reviverPlayer.sendMessage(Message.raw("§e✚✚✚ REVIVE STARTED! RIGHT-CLICKED PLAYER! ✚✚✚ [RELOAD TEST]"))

        println("[HyDowned] ✓ Revive queued - DownedTimerSystem will process it")
        println("[HyDowned] ============================================")
    }
}
