package com.hydowned.network

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains
import com.hypixel.hytale.protocol.packets.player.ClientMovement
import com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock
import com.hypixel.hytale.protocol.packets.player.MouseInteraction
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import java.util.function.Consumer
import com.hydowned.util.Log


/**
 * Packet interceptor for downed players.
 *
 * This wraps both incoming and outgoing packet handlers to intercept and manipulate
 * packets for players in the downed state.
 *
 * INCOMING (Client -> Server):
 * - ClientMovement: Filters out position changes, allows head/body rotation
 * - MouseInteraction: Blocks all mouse interactions
 * - SyncInteractionChains: Blocks interaction chains (block breaking, etc.)
 * - ClientPlaceBlock: Blocks block placement
 *
 * OUTGOING (Server -> Client):
 * - PlayAnimation: Blocks any non-death animations in Movement and Status slots
 * - EntityUpdates: Forces MovementStates.sleeping = true for downed player
 * - Any packets that would override downed state: Blocked or modified
 */
class DownedPacketInterceptor(
    private val playerRef: Ref<EntityStore>,
    private val originalHandler: Consumer<Packet>,
    private val originalPacketHandler: PacketHandler
) {

    /**
     * Wraps an incoming packet handler to intercept packets from the client
     */
    fun createIncomingWrapper(): Consumer<Packet> {
        return Consumer { packet ->
            val isDowned = isPlayerDowned()

            if (isDowned) {
                when (packet) {
                    // COMMENTED OUT - Using phantom body approach, let player move freely
                    // is ClientMovement -> {
                    //     // Allow head/body rotation, but block position changes
                    //     handleClientMovement(packet, originalHandler)
                    // }
                    is MouseInteraction -> {
                        // Block all mouse interactions while downed
                        Log.verbose("PacketInterceptor", "BLOCKED MouseInteraction")
                        // Don't call original handler - packet is blocked
                    }
                    is SyncInteractionChains -> {
                        // Block interaction chains (block breaking, item use, etc.)
                        Log.verbose("PacketInterceptor", "BLOCKED SyncInteractionChains (${packet.updates.size} interactions)")
                        // Don't call original handler - packet is blocked
                    }
                    is ClientPlaceBlock -> {
                        // Block block placement
                        Log.verbose("PacketInterceptor", "BLOCKED ClientPlaceBlock")
                        // Don't call original handler - packet is blocked
                    }
                    else -> {
                        // Allow other packets through (including ClientMovement for phantom body approach)
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
     * Creates a wrapped PacketHandler for outgoing packets
     */
    fun createOutgoingWrapper(): PacketHandlerWrapper {
        return PacketHandlerWrapper(originalPacketHandler, playerRef)
    }

    /**
     * Handles ClientMovement packets - allows rotation but blocks position changes
     */
    private fun handleClientMovement(packet: ClientMovement, originalHandler: Consumer<Packet>) {
        // If this is a teleport acknowledgment, allow it through unmodified
        // (our DownedTeleportLockSystem sends teleports, client must acknowledge them)
        if (packet.teleportAck != null) {
            originalHandler.accept(packet)
            return
        }

        // Create a modified packet that has position fields nulled out
        val modifiedPacket = ClientMovement()

        // ALLOW rotation (head/body)
        modifiedPacket.bodyOrientation = packet.bodyOrientation
        modifiedPacket.lookOrientation = packet.lookOrientation

        // CREATE NEW movement states object with sleeping forced
        // We must create a new object, not modify the existing one
        val originalStates = packet.movementStates
        if (originalStates != null) {
            // Create a completely new MovementStates with only sleeping=true and onGround preserved
            val forcedStates = MovementStates()
            forcedStates.sleeping = true
            forcedStates.idle = false
            forcedStates.horizontalIdle = false
            forcedStates.walking = false
            forcedStates.running = false
            forcedStates.sprinting = false
            forcedStates.jumping = false
            forcedStates.falling = false
            forcedStates.crouching = false
            forcedStates.forcedCrouching = false
            forcedStates.climbing = false
            forcedStates.flying = false
            forcedStates.inFluid = false
            forcedStates.swimming = false
            forcedStates.swimJumping = false
            forcedStates.mantling = false
            forcedStates.sliding = false
            forcedStates.mounting = false
            forcedStates.rolling = false
            forcedStates.sitting = false
            forcedStates.gliding = false
            forcedStates.onGround = originalStates.onGround // Preserve ground state
            modifiedPacket.movementStates = forcedStates
        }

        // BLOCK position changes and teleportAck
        modifiedPacket.relativePosition = null
        modifiedPacket.absolutePosition = null
        modifiedPacket.wishMovement = null
        modifiedPacket.velocity = null
        modifiedPacket.teleportAck = null

        // Pass modified packet to original handler
        originalHandler.accept(modifiedPacket)
    }

    /**
     * Checks if the player is currently downed (thread-safe)
     */
    private fun isPlayerDowned(): Boolean {
        if (!playerRef.isValid) return false
        return DownedStateTracker.isDowned(playerRef)
    }

    /**
     * Wrapper for outgoing PacketHandler that intercepts packets being sent to client
     */
    class PacketHandlerWrapper(
        private val delegate: PacketHandler,
        private val playerRef: Ref<EntityStore>
    ) : PacketHandler(delegate.channel, delegate.protocolVersion) {

        override fun getIdentifier(): String {
            return delegate.getIdentifier()
        }

        override fun accept(packet: Packet) {
            // This handles incoming packets - delegate to original handler
            delegate.accept(packet)
        }

        override fun write(packet: Packet) {
            // COMMENTED OUT - Using phantom body approach, don't intercept animations/EntityUpdates
            // Player is invisible, phantom body shows the animation instead
            // if (isPlayerDowned()) {
            //     when (packet) {
            //         is PlayAnimation -> {
            //             // Block any animations that aren't Death in Movement or Status slots
            //             if (packet.slot == AnimationSlot.Movement || packet.slot == AnimationSlot.Status) {
            //                 val animationId = packet.animationId
            //                 if (animationId != null && !animationId.contains("Death", ignoreCase = true)) {
            //                     // Block non-death animations
            //                     Log.verbose("PacketInterceptor", "Blocked non-death animation in ${packet.slot} slot: $animationId")
            //                     return // Don't send packet
            //                 }
            //             }
            //         }
            //         is EntityUpdates -> {
            //             // Intercept EntityUpdates to force sleeping movement state
            //             handleEntityUpdates(packet)
            //         }
            //     }
            // }

            // Send packet normally
            delegate.write(packet)
        }

        override fun writeNoCache(packet: Packet) {
            // COMMENTED OUT - Using phantom body approach, don't intercept animations/EntityUpdates
            // if (isPlayerDowned()) {
            //     when (packet) {
            //         is PlayAnimation -> {
            //             // Block any animations that aren't Death in Movement or Status slots
            //             if (packet.slot == AnimationSlot.Movement || packet.slot == AnimationSlot.Status) {
            //                 val animationId = packet.animationId
            //                 if (animationId != null && !animationId.contains("Death", ignoreCase = true)) {
            //                     Log.verbose("PacketInterceptor", "Blocked non-death animation in ${packet.slot} slot: $animationId")
            //                     return
            //                 }
            //             }
            //         }
            //         is EntityUpdates -> {
            //             // Intercept EntityUpdates to force sleeping movement state
            //             handleEntityUpdates(packet)
            //         }
            //     }
            // }

            delegate.writeNoCache(packet)
        }

        /**
         * Handles EntityUpdates packets to force sleeping movement state
         */
        private fun handleEntityUpdates(packet: EntityUpdates) {
            // Get current network ID from tracker (thread-safe, handles respawns/changes)
            val currentNetworkId = DownedStateTracker.getNetworkId(playerRef)
            if (currentNetworkId == null) {
                // Network ID not tracked yet, skip
                return
            }

            // Check if packet contains updates
            val updates = packet.updates ?: return

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
                                movementStates.walking = false
                                movementStates.running = false
                                movementStates.sprinting = false
                                movementStates.jumping = false
                                movementStates.falling = false
                                movementStates.crouching = false
                                movementStates.forcedCrouching = false
                                Log.verbose("PacketInterceptor", "Modified MovementStates in EntityUpdates - forced sleeping = true, blocked crouching")
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
    }
}
