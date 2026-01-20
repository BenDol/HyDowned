package com.hydowned.listeners

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.config.DownedConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for PlayerReadyEvent (player login) and queues cleanup.
 *
 * Queues players that need login sanity checks so DownedLoginCleanupSystem
 * can process them with proper commandBuffer access.
 */
class PlayerReadyEventListener(
    private val config: DownedConfig
) {
    companion object {
        // Queue of pending login cleanups (playerEntityRef -> player UUID)
        val pendingLoginCleanups = ConcurrentHashMap<Ref<EntityStore>, String>()
    }

    fun onPlayerReady(event: PlayerReadyEvent) {
        // Get player's entity reference and UUID
        val playerRef = event.playerRef
        val player = event.player
        val playerUUID = player.uuid.toString()

        // Safe access to display name (may be null at login time)
        val playerName = try {
            player.displayName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown (${playerUUID.take(8)})"
        }

        println("[HyDowned] [PlayerReady] Player logged in: $playerName")

        // Queue this player for login cleanup processing
        pendingLoginCleanups[playerRef] = playerUUID

        println("[HyDowned] [PlayerReady] ✓ Queued player for login cleanup")
        println("[HyDowned] [PlayerReady] Queue size: ${pendingLoginCleanups.size}")
    }
}
