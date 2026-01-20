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

        val packetHandler = playerRefComponent.getPacketHandler()

        // Check if it's a GenericPacketHandler (it should be)
        if (packetHandler !is GenericPacketHandler) {
            println("[HyDowned] Warning: PacketHandler is not GenericPacketHandler, cannot intercept")
            return
        }

        // Get the player's network ID (on world thread - safe to access ECS)
        val networkIdComponent = commandBuffer.getComponent(ref, NetworkId.getComponentType())
        if (networkIdComponent == null) {
            println("[HyDowned] Warning: NetworkId component not found, cannot install packet interceptors")
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

            println("[HyDowned] [INSTALL] Found ${handlers.size} handler slots in GenericPacketHandler")

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
                        packetHandler
                    )

                    // Replace with wrapped handler
                    handlers[i] = interceptor.createIncomingWrapper()
                    wrappedCount++
                }
            }

            // 2. OUTGOING PACKET HANDLER WRAPPING - DISABLED
            // NOTE: Java 17+ doesn't allow modifying Field.modifiers, so we can't replace
            // the final packetHandler field in PlayerRef. The incoming packet wrapping
            // should be sufficient for blocking interactions.

            Log.verbose("PacketInterceptor", "Installed packet interceptors for player:")
            println("[HyDowned]   - Wrapped $wrappedCount incoming handlers")
            println("[HyDowned]   - Outgoing handler wrapping DISABLED (Java 17+ limitation)")
            println("[HyDowned]   - NetworkId=$playerNetworkId stored in tracker")

        } catch (e: Exception) {
            println("[HyDowned] Failed to install packet interceptors: ${e.message}")
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

        // Clean up downed state tracking
        DownedStateTracker.remove(ref)
    }
}
