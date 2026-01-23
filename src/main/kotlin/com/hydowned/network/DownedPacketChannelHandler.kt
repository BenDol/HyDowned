package com.hydowned.network

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.util.Log
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

/**
 * Netty channel handler that intercepts outgoing packets for downed players.
 *
 * This is inserted into the Netty pipeline to intercept ALL packets being sent
 * to the client, allowing us to:
 * 1. Modify EntityUpdates packets to force sleeping=true
 * 2. Block PlayAnimation packets that aren't Death animations in Movement/Status slots
 *
 * This works where PacketHandlerWrapper couldn't because:
 * - We operate at the Netty channel level (lower than PacketHandler)
 * - No need to extend GamePacketHandler (avoids final method issues)
 * - Intercepts ALL packets regardless of which system sent them
 */
class DownedPacketChannelHandler(
    private val playerRef: Ref<EntityStore>,
    private val usePlayerMode: Boolean
) : ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        // Intercept packets for downed players in PLAYER mode
        if (msg is Packet && usePlayerMode && isPlayerDowned()) {
            when (msg) {
                is EntityUpdates -> {
                    // Modify EntityUpdates to force sleeping=true for the local player
                    //Log.finer("ChannelHandler", "Intercepted EntityUpdates packet - modifying sleeping state")
                    handleEntityUpdates(msg)
                }
                is PlayAnimation -> {
                    // Block any non-Death/Sleep animations in Movement or Status slots
                    if (msg.slot == AnimationSlot.Movement || msg.slot == AnimationSlot.Status) {
                        val animationId = msg.animationId

                        if (animationId != null &&
                            !animationId.contains("Death", ignoreCase = true) &&
                            !animationId.contains("Sleep", ignoreCase = true)) {
                            // BLOCK non-downed animations - don't send to client
                            return // Don't call super.write - packet is blocked
                        }
                    }
                }
            }
        }

        // Always pass the message through (unless blocked above)
        super.write(ctx, msg, promise)
    }

    /**
     * Handles EntityUpdates packets to force sleeping movement state
     */
    private fun handleEntityUpdates(packet: EntityUpdates) {
        // Get current network ID from tracker (thread-safe, handles respawns/changes)
        val currentNetworkId = DownedStateTracker.getNetworkId(playerRef)
        if (currentNetworkId == null) {
            Log.finer("ChannelHandler", "No network ID tracked for player, skipping")
            return
        }

        // Check if packet contains updates
        val updates = packet.updates
        if (updates == null) {
            Log.finer("ChannelHandler", "EntityUpdates packet has no updates")
            return
        }

        // Find EntityUpdate for this player (using current network ID from tracker)
        for (entityUpdate in updates) {
            if (entityUpdate.networkId == currentNetworkId) {
                // Found update for downed player
                val componentUpdates = entityUpdate.updates ?: continue

                // Find MovementStates update
                for (componentUpdate in componentUpdates) {
                    if (componentUpdate.type == ComponentUpdateType.MovementStates) {
                        val movementStates = componentUpdate.movementStates
                        if (movementStates != null) {
                            // Force sleeping state to keep lying down animation
                            movementStates.sleeping = true
                            movementStates.idle = false
                            movementStates.horizontalIdle = false
                            movementStates.walking = false
                            movementStates.running = false
                            movementStates.sprinting = false
                            movementStates.jumping = false
                            movementStates.falling = false
                            movementStates.crouching = false
                            movementStates.forcedCrouching = false
                            movementStates.climbing = false
                            movementStates.flying = false
                            movementStates.inFluid = false
                            movementStates.swimming = false
                            movementStates.swimJumping = false
                            movementStates.mantling = false
                            movementStates.sliding = false
                            movementStates.mounting = false
                            movementStates.rolling = false
                            movementStates.sitting = false
                            movementStates.gliding = false
                            Log.finer("ChannelHandler", "Modified MovementStates to sleeping=true")
                        }
                    }
                }
            }
        }
    }

    private fun isPlayerDowned(): Boolean {
        if (!playerRef.isValid) return false
        return DownedStateTracker.isDowned(playerRef)
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.warning("ChannelHandler", "Exception in packet channel handler: ${cause.message}")
        cause.printStackTrace()
        super.exceptionCaught(ctx, cause)
    }
}
