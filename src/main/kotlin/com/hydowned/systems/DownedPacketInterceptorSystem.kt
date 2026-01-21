package com.hydowned.systems

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.config.DownedConfig
import com.hydowned.network.DownedPacketInterceptor
import com.hydowned.network.DownedPacketChannelHandler
import com.hydowned.network.DownedStateTracker
import com.hydowned.util.Log


/**
 * Installs packet interceptors for all players.
 *
 * This system hooks into the packet handling layer to intercept both incoming
 * and outgoing packets for downed players.
 *
 * When a player joins:
 * 1. Wraps their GamePacketHandler's incoming packet handlers
 * 2. Wraps their outgoing PacketHandler
 *
 * This allows us to:
 * - Block/modify incoming packets (movement, interactions)
 * - Block/modify outgoing packets (animations that override downed state)
 *
 * This is more elegant than continuously spamming packets - we intercept at the
 * packet layer and prevent unwanted state changes from happening in the first place.
 */
class DownedPacketInterceptorSystem(
    private val config: DownedConfig
) : RefSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        PlayerRef.getComponentType()
    )

    private val installedInterceptors = mutableMapOf<Ref<EntityStore>, DownedPacketInterceptor>()

    override fun getQuery(): Query<EntityStore> = query

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get the player's packet handler
        val playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            ?: return

        val packetHandler = playerRefComponent.packetHandler

        // Check if it's a GenericPacketHandler (it should be)
        if (packetHandler !is GenericPacketHandler) {
            Log.verbose("PacketInterceptor", "Warning: PacketHandler is not GenericPacketHandler (is ${packetHandler::class.java.name}), cannot intercept")
            return
        }

        // Get the player's network ID (on world thread - safe to access ECS)
        val networkIdComponent = commandBuffer.getComponent(ref, NetworkId.getComponentType())
        if (networkIdComponent == null) {
            Log.verbose("PacketInterceptor", "Warning: NetworkId component not found, cannot install packet interceptors")
            return
        }
        val playerNetworkId = networkIdComponent.id

        // Store network ID in tracker for thread-safe access from network threads
        DownedStateTracker.setNetworkId(ref, playerNetworkId)

        try {
            // 1. WRAP INCOMING PACKET HANDLERS
            val handlersField = GenericPacketHandler::class.java.getDeclaredField("handlers")
            handlersField.isAccessible = true
            val handlers = handlersField.get(packetHandler) as Array<Any?>

            Log.verbose("PacketInterceptor", "Found ${handlers.size} handler slots in GenericPacketHandler")

            // Count how many we wrap
            var wrappedCount = 0

            // Wrap all existing handlers
            for (i in handlers.indices) {
                val originalHandler = handlers[i]
                if (originalHandler != null) {
                    @Suppress("UNCHECKED_CAST")
                    val interceptor = DownedPacketInterceptor(
                        ref,
                        originalHandler as java.util.function.Consumer<com.hypixel.hytale.protocol.Packet>,
                        packetHandler,
                        config.usePlayerMode
                    )

                    // Replace with wrapped handler
                    handlers[i] = interceptor.createIncomingWrapper()
                    wrappedCount++
                }
            }

            // 2. INSTALL NETTY CHANNEL HANDLER FOR OUTGOING PACKETS
            // We can't extend GamePacketHandler due to final methods, so we intercept at the
            // Netty channel level instead. This gives us access to ALL outgoing packets.
            try {
                val channel = packetHandler.channel
                val pipeline = channel.pipeline()

                // Create our channel handler
                val channelHandler = DownedPacketChannelHandler(ref, config.usePlayerMode)

                // Add it to the pipeline BEFORE the encoder (so we can modify packets before encoding)
                // Use a unique name based on player ref to avoid conflicts
                val handlerName = "hydowned_packet_interceptor_${ref.hashCode()}"

                if (pipeline.get(handlerName) == null) {
                    // Add after "packet_handler" or at the end if not found
                    if (pipeline.get("packet_handler") != null) {
                        pipeline.addAfter("packet_handler", handlerName, channelHandler)
                    } else {
                        pipeline.addLast(handlerName, channelHandler)
                    }
                    Log.verbose("PacketInterceptor", "Installed Netty channel handler for outgoing packets")
                }
            } catch (e: Exception) {
                Log.warning("PacketInterceptor", "Failed to install Netty channel handler: ${e.message}")
                e.printStackTrace()
            }

            Log.verbose("PacketInterceptor", "Installed packet interceptors for player:")
            Log.verbose("PacketInterceptor", "  - Wrapped $wrappedCount incoming handlers (ClientMovement BLOCKED)")
            Log.verbose("PacketInterceptor", "  - Installed Netty channel handler (intercepts ALL outgoing EntityUpdates)")
            Log.verbose("PacketInterceptor", "  - NetworkId=$playerNetworkId stored in tracker")

        } catch (e: Exception) {
            Log.verbose("PacketInterceptor", "Failed to install packet interceptors: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Clean up interceptor
        installedInterceptors.remove(ref)

        // Clean up Netty channel handler
        try {
            val playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            if (playerRefComponent != null) {
                val packetHandler = playerRefComponent.packetHandler
                if (packetHandler is GenericPacketHandler) {
                    val channel = packetHandler.channel
                    val pipeline = channel.pipeline()
                    val handlerName = "hydowned_packet_interceptor_${ref.hashCode()}"

                    if (pipeline.get(handlerName) != null) {
                        pipeline.remove(handlerName)
                        Log.verbose("PacketInterceptor", "Removed Netty channel handler for disconnecting player")
                    }
                }
            }
        } catch (e: Exception) {
            Log.verbose("PacketInterceptor", "Failed to remove Netty channel handler: ${e.message}")
        }

        // Clean up downed state tracking
        DownedStateTracker.remove(ref)
    }
}
