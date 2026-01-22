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
import com.hydowned.network.DownedStateTracker
import com.hydowned.util.ComponentUtils
import com.hydowned.util.DisplayNameUtils
import com.hydowned.util.PendingDeathTracker
import com.hydowned.util.Log
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers
import com.hypixel.hytale.server.core.modules.entity.component.Intangible
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.universe.PlayerRef


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

        Log.finer("LogoutHandler", "Downed player logging out: $playerUuid")
        Log.finer("LogoutHandler", "Reason: $reason")

        // Get the DownedComponent
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
            ?: return

        Log.finer("LogoutHandler", "Player quit while downed")

        /**
         * Determine if this is an intentional logout or a crash/disconnect
         * UNLOAD = kicked by duplicate login / server shutdown (could be crash - mark for restore)
         * DISCONNECT = intentional logout (mark for death)
         * For now, log all reasons so we can see what we get
         */
        when (reason.toString()) {
            "DISCONNECT" -> {
                // Intentional logout - player should die on rejoin
                Log.finer("LogoutHandler", "Detected INTENTIONAL logout (DISCONNECT)")
                if (playerUuid != null) {
                    PendingDeathTracker.markForDeath(playerUuid)
                    Log.finer("LogoutHandler", "Marked player for death on rejoin")
                } else {
                    Log.warning("LogoutHandler", "Player UUID is null, cannot mark for death")
                }
            }
            "UNLOAD" -> {
                // Unload (duplicate login, server shutdown) - could be crash, preserve downed state
                Log.finer("LogoutHandler", "Detected UNINTENTIONAL disconnect (UNLOAD) - preserving downed state")
                markForRestore(playerUuid, downedComponent)
            }
            else -> {
                // Unknown reason - log it for investigation, default to crash recovery (be lenient)
                Log.warning("LogoutHandler", "Unknown RemoveReason: $reason - defaulting to crash recovery")
                markForRestore(playerUuid, downedComponent)
            }
        }

        // CRITICAL: Restore components BEFORE entity removal completes
        // If we don't do this, Hytale's PlayerRemovedSystem may crash or save incorrect state

        // 1. Restore original DisplayNameComponent
        val originalDisplayName = downedComponent.originalDisplayName
        if (originalDisplayName != null) {
            try {
                // Remove empty DisplayNameComponent and restore original
                commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())
                commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)
                Log.finer("LogoutHandler", "Restored original DisplayNameComponent")
            } catch (e: Exception) {
                Log.warning("LogoutHandler", "Failed to restore DisplayNameComponent: ${e.message}")
                e.printStackTrace()
                // Ensure SOMETHING exists even if restore fails
                commandBuffer.ensureComponent(ref, DisplayNameComponent.getComponentType())
            }
        } else {
            Log.warning("LogoutHandler", "No original DisplayNameComponent stored, ensuring one exists")
            commandBuffer.ensureComponent(ref, DisplayNameComponent.getComponentType())
        }

        // 2. Restore visibility (remove HiddenFromAdventurePlayers if we added it)
        if (downedComponent.wasVisibleBefore) {
            ComponentUtils.removeComponentSafely(
                ref, commandBuffer,
                HiddenFromAdventurePlayers.getComponentType(),
                "HiddenFromAdventurePlayers",
                "LogoutHandler"
            )
        }

        // 3. Restore collision (re-enable character collisions)
        try {
            val collisionResult = commandBuffer.getComponent(ref, CollisionResultComponent.getComponentType())
            if (collisionResult != null && downedComponent.hadCollisionEnabled) {
                // Note: API method has typo - "Collsions" instead of "Collisions"
                collisionResult.collisionResult.enableCharacterCollsions()
                Log.finer("LogoutHandler", "Re-enabled character collisions")
            }
        } catch (e: Exception) {
            Log.warning("LogoutHandler", "Failed to re-enable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // 3b. Remove any lingering Intangible component (defensive cleanup)
        ComponentUtils.removeComponentSafely(
            ref, commandBuffer,
            Intangible.getComponentType(),
            "Intangible (defensive cleanup)",
            "LogoutHandler"
        )

        // 4. Clean up phantom body manually (callback won't fire during entity removal)
        val phantomBodyRef = downedComponent.phantomBodyRef
        if (phantomBodyRef != null && phantomBodyRef.isValid) {
            commandBuffer.removeEntity(phantomBodyRef, RemoveReason.UNLOAD)
            Log.finer("LogoutHandler", "Removed phantom body entity")
        }

        // 5. Restore scale manually (callback won't fire during entity removal)
        try {
            val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
            if (scaleComponent != null) {
                scaleComponent.scale = downedComponent.originalScale
                Log.finer("LogoutHandler", "Restored scale to ${downedComponent.originalScale}")
            }
        } catch (e: Exception) {
            Log.warning("LogoutHandler", "Failed to restore scale: ${e.message}")
            e.printStackTrace()
        }

        // 6. Restore Interactable component
        ComponentUtils.ensureComponentSafely(
            ref, commandBuffer,
            Interactable.getComponentType(),
            "Interactable",
            "LogoutHandler"
        )

        // 7. Reset camera for PLAYER mode
        try {
            val playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            if (playerRefComponent != null) {
                val cameraSystem = com.hydowned.HyDownedPlugin.instance?.getCameraSystem()
                if (cameraSystem != null) {
                    cameraSystem.resetCameraForPlayer(playerRefComponent, commandBuffer)
                    Log.finer("LogoutHandler", "Reset camera to normal view")
                }
            }
        } catch (e: Exception) {
            Log.warning("LogoutHandler", "Failed to reset camera: ${e.message}")
        }

        // 8. Remove DownedComponent and clear state tracker
        // (Restoration is handled by file-based PendingDeathTracker, not in-memory component)
        commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())
        DownedStateTracker.setNotDowned(ref)

        Log.finer("LogoutHandler", "Cleaned up downed state (restoration data saved to disk)")
    }

    /**
     * Helper function to mark a player for restore with proper null checks and logging.
     */
    private fun markForRestore(playerUuid: java.util.UUID?, downedComponent: DownedComponent) {
        if (playerUuid != null) {
            val timeRemaining = downedComponent.downedTimeRemaining
            val downedLocation = downedComponent.downedLocation
            PendingDeathTracker.markForRestore(playerUuid, timeRemaining, downedLocation)
            Log.finer("LogoutHandler", "Marked player to restore downed state with $timeRemaining seconds at $downedLocation on rejoin")
        } else {
            Log.warning("LogoutHandler", "Player UUID is null, cannot mark for restore")
        }
    }
}
