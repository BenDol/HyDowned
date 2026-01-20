package com.hydowned.systems

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.PendingDeathTracker

/**
 * Handles when a downed player logs out (entity is removed from world).
 *
 * When a player with DownedComponent is being removed, executes death using
 * the centralized DownedCleanupHelper to ensure:
 * - Player dies at their phantom body location
 * - Player is no longer marked as invisible
 * - Phantom body is cleaned up
 * - Player will respawn normally when they log back in
 */
class DownedLogoutHandlerSystem(
    private val config: DownedConfig
) : RefSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - we only care about entity removal
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get player UUID for logging
        val uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
        val playerUuid = uuidComponent?.uuid

        println("[HyDowned] [LogoutHandler] Downed player logging out: $playerUuid")
        println("[HyDowned] [LogoutHandler] Reason: $reason")

        // Get the DownedComponent
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
            ?: return

        println("[HyDowned] [LogoutHandler] Player quit while downed")

        // Determine if this is an intentional logout or a crash/disconnect
        // UNLOAD = kicked by duplicate login / server shutdown (could be crash - mark for restore)
        // DISCONNECT = intentional logout (mark for death)
        // For now, log all reasons so we can see what we get
        when (reason.toString()) {
            "DISCONNECT" -> {
                // Intentional logout - player should die on rejoin
                println("[HyDowned] [LogoutHandler] Detected INTENTIONAL logout (DISCONNECT)")
                if (playerUuid != null) {
                    PendingDeathTracker.markForDeath(playerUuid)
                    println("[HyDowned] [LogoutHandler] ✓ Marked player for death on rejoin")
                } else {
                    println("[HyDowned] [LogoutHandler] ⚠ Player UUID is null, cannot mark for death")
                }
            }
            "UNLOAD" -> {
                // Unload (duplicate login, server shutdown) - could be crash, preserve downed state
                println("[HyDowned] [LogoutHandler] Detected UNINTENTIONAL disconnect (UNLOAD) - preserving downed state")
                if (playerUuid != null) {
                    val timeRemaining = downedComponent.downedTimeRemaining
                    val downedLocation = downedComponent.downedLocation
                    PendingDeathTracker.markForRestore(playerUuid, timeRemaining, downedLocation)
                    println("[HyDowned] [LogoutHandler] ✓ Marked player to restore downed state with $timeRemaining seconds at $downedLocation on rejoin")
                } else {
                    println("[HyDowned] [LogoutHandler] ⚠ Player UUID is null, cannot mark for restore")
                }
            }
            else -> {
                // Unknown reason - log it for investigation, default to crash recovery (be lenient)
                println("[HyDowned] [LogoutHandler] ⚠ Unknown RemoveReason: $reason - defaulting to crash recovery")
                if (playerUuid != null) {
                    val timeRemaining = downedComponent.downedTimeRemaining
                    val downedLocation = downedComponent.downedLocation
                    PendingDeathTracker.markForRestore(playerUuid, timeRemaining, downedLocation)
                    println("[HyDowned] [LogoutHandler] ✓ Marked player to restore downed state with $timeRemaining seconds at $downedLocation on rejoin")
                } else {
                    println("[HyDowned] [LogoutHandler] ⚠ Player UUID is null, cannot mark for restore")
                }
            }
        }

        // CRITICAL: Restore components BEFORE entity removal completes
        // If we don't do this, Hytale's PlayerRemovedSystem may crash or save incorrect state

        // 1. Restore original DisplayNameComponent
        val originalDisplayName = downedComponent.originalDisplayName
        if (originalDisplayName != null) {
            try {
                // Remove empty DisplayNameComponent and restore original
                commandBuffer.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType())
                commandBuffer.addComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType(), originalDisplayName)
                println("[HyDowned] [LogoutHandler] ✓ Restored original DisplayNameComponent")
            } catch (e: Exception) {
                println("[HyDowned] [LogoutHandler] ⚠ Failed to restore DisplayNameComponent: ${e.message}")
                e.printStackTrace()
                // Ensure SOMETHING exists even if restore fails
                commandBuffer.ensureComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType())
            }
        } else {
            println("[HyDowned] [LogoutHandler] ⚠ No original DisplayNameComponent stored, ensuring one exists")
            commandBuffer.ensureComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType())
        }

        // 2. Restore visibility (remove HiddenFromAdventurePlayers if we added it)
        try {
            if (downedComponent.wasVisibleBefore) {
                commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers.getComponentType())
                println("[HyDowned] [LogoutHandler] ✓ Removed HiddenFromAdventurePlayers")
            }
        } catch (e: Exception) {
            println("[HyDowned] [LogoutHandler] ⚠ Failed to remove HiddenFromAdventurePlayers: ${e.message}")
            e.printStackTrace()
        }

        // 3. Restore collision (re-enable character collisions)
        try {
            val collisionResult = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent.getComponentType())
            if (collisionResult != null && downedComponent.hadCollisionEnabled) {
                // Note: API method has typo - "Collsions" instead of "Collisions"
                collisionResult.collisionResult.enableCharacterCollsions()
                println("[HyDowned] [LogoutHandler] ✓ Re-enabled character collisions")
            }
        } catch (e: Exception) {
            println("[HyDowned] [LogoutHandler] ⚠ Failed to re-enable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // 3b. Remove any lingering Intangible component (defensive cleanup)
        try {
            commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.Intangible.getComponentType())
        } catch (e: Exception) {
            // Ignore - component may not exist
        }

        // 4. Clean up phantom body manually (callback won't fire during entity removal)
        val phantomBodyRef = downedComponent.phantomBodyRef
        if (phantomBodyRef != null && phantomBodyRef.isValid) {
            commandBuffer.removeEntity(phantomBodyRef, com.hypixel.hytale.component.RemoveReason.UNLOAD)
            println("[HyDowned] [LogoutHandler] ✓ Removed phantom body entity")
        }

        // 5. Restore scale manually (callback won't fire during entity removal)
        try {
            val scaleComponent = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType())
            if (scaleComponent != null) {
                scaleComponent.scale = downedComponent.originalScale
                println("[HyDowned] [LogoutHandler] ✓ Restored scale to ${downedComponent.originalScale}")
            }
        } catch (e: Exception) {
            println("[HyDowned] [LogoutHandler] ⚠ Failed to restore scale: ${e.message}")
            e.printStackTrace()
        }

        // 6. Restore Interactable component
        commandBuffer.ensureComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.Interactable.getComponentType())
        println("[HyDowned] [LogoutHandler] ✓ Ensured Interactable component exists")

        // 7. Remove DownedComponent and clear state tracker
        // (Restoration is handled by file-based PendingDeathTracker, not in-memory component)
        commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())
        com.hydowned.network.DownedStateTracker.setNotDowned(ref)

        println("[HyDowned] [LogoutHandler] ✓ Cleaned up downed state (restoration data saved to disk)")
    }
}
