package com.hydowned.network

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains
import com.hypixel.hytale.protocol.packets.player.ClientMovement
import com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock
import com.hypixel.hytale.protocol.packets.player.MouseInteraction
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.function.Consumer
import com.hydowned.util.Log


/**
 * Packet interceptor for downed players.
 *
 * This wraps incoming packet handlers to intercept and block packets
 * from downed players.
 *
 * INCOMING (Client -> Server):
 * - ClientMovement: PLAYER mode only - Completely blocked
 * - MouseInteraction: Blocks all mouse interactions
 * - SyncInteractionChains: Blocks interaction chains (block breaking, etc.)
 * - ClientPlaceBlock: Blocks block placement
 */
class DownedPacketInterceptor(
    private val playerRef: Ref<EntityStore>,
    private val originalHandler: Consumer<Packet>,
    private val originalPacketHandler: PacketHandler,
    private val usePlayerMode: Boolean
) {

    /**
     * Wraps an incoming packet handler to intercept packets from the client
     */
    fun createIncomingWrapper(): Consumer<Packet> {
        return Consumer { packet ->
            val isDowned = isPlayerDowned()

            if (isDowned) {
                when (packet) {
                    is ClientMovement -> {
                        if (usePlayerMode) {
                            // PLAYER mode - COMPLETELY BLOCK ClientMovement packets
                            Log.finer("PacketInterceptor", "BLOCKED ClientMovement (PLAYER mode)")
                        } else {
                            // PHANTOM mode - allow movement freely
                            originalHandler.accept(packet)
                        }
                    }
                    is MouseInteraction -> {
                        // Block all mouse interactions while downed
                        Log.finer("PacketInterceptor", "BLOCKED MouseInteraction")
                    }
                    is SyncInteractionChains -> {
                        // Block interaction chains (block breaking, item use, etc.)
                        Log.finer("PacketInterceptor", "BLOCKED SyncInteractionChains (${packet.updates.size} interactions)")
                    }
                    is ClientPlaceBlock -> {
                        // Block block placement
                        Log.finer("PacketInterceptor", "BLOCKED ClientPlaceBlock")
                    }
                    else -> {
                        // Allow other packets through
                        originalHandler.accept(packet)
                    }
                }
            } else {
                // Not downed - pass through normally
                originalHandler.accept(packet)
            }
        }
    }

    /**
     * Checks if the player is currently downed (thread-safe)
     */
    private fun isPlayerDowned(): Boolean {
        if (!playerRef.isValid) return false
        return DownedStateTracker.isDowned(playerRef)
    }
}
