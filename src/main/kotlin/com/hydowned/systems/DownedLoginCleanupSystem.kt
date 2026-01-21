package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.listeners.PlayerReadyEventListener
import com.hydowned.util.DownedCleanupHelper
import com.hydowned.util.PendingDeathTracker
import com.hydowned.network.DownedStateTracker
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hydowned.util.Log
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers
import com.hypixel.hytale.server.core.modules.entity.component.Intangible
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef


/**
 * Sanity check system that processes pending login cleanups.
 *
 * Runs every 1 second and processes players queued by PlayerReadyEventListener.
 *
 * Handles three scenarios:
 * 1. Player logged out while downed (PendingDeathTracker has record) → Execute death on login
 * 2. Player crashed while downed (DownedComponent exists, no death tracker record) → Re-initialize downed state
 *    - Removes and re-adds DownedComponent to trigger all RefChangeSystem callbacks
 *    - This causes DownedPhantomBodySystem, DownedPlayerScaleSystem, etc. to set up fresh
 *    - Timer is preserved from the cloned component
 * 3. Normal login (no DownedComponent) → Run standard cleanup
 *
 * Standard cleanup operations:
 * - Restores scale to 1.0 (crash safety)
 * - Ensures DisplayNameComponent exists
 * - Ensures Interactable component exists
 * - Removes HiddenFromAdventurePlayers component (invisibility mode cleanup)
 * - Removes Intangible component (collision cleanup)
 * - Ensures PlayerSkinComponent exists with valid skin data (restores default appearance)
 *
 * This prevents players from being invisible/broken after logging back in while also
 * allowing crash recovery - if a player crashes while downed, they continue being downed.
 */
class DownedLoginCleanupSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(1.0f) {

    private val query = Query.and(
        Player.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val ref = archetypeChunk.getReferenceTo(index)

        // CRITICAL CRASH FIX: Remove HiddenFromAdventurePlayers for NON-DOWNED players
        // This component causes client crashes when combined with camera system
        // Must happen BEFORE any queue-based cleanup to fix broken characters
        // BUT: Don't remove from currently downed players (they need to be invisible!)
        val isDowned = commandBuffer.getComponent(ref, DownedComponent.getComponentType()) != null
        val hadHiddenCrashFix = commandBuffer.getComponent(ref, HiddenFromAdventurePlayers.getComponentType()) != null
        if (hadHiddenCrashFix && !isDowned) {
            commandBuffer.tryRemoveComponent(ref, HiddenFromAdventurePlayers.getComponentType())
            Log.finer("LoginCleanup", "Removed HiddenFromAdventurePlayers (crash fix - player not downed)")
        }

        // Check if this player has pending login cleanup for full processing
        val pendingUUID = PlayerReadyEventListener.pendingLoginCleanups.remove(ref)
        if (pendingUUID == null) {
            return // No additional cleanup needed for this player
        }

        Log.finer("LoginCleanup", "Processing login cleanup for player...")

        var issuesFound = false

        // Get player UUID
        val uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
        val playerUuid = uuidComponent?.uuid

        // 1. Check PendingDeathTracker for any pending actions (death or restore)
        if (playerUuid != null) {
            when (val action = PendingDeathTracker.checkAndClearAction(playerUuid)) {
                is PendingDeathTracker.RestoreAction.ExecuteDeath -> {
                    // Player intentionally logged out while downed → execute death
                    Log.finer("LoginCleanup", "Player logged out while downed - executing death")

                    // Create a minimal DownedComponent for death execution
                    val tempDownedComponent = DownedComponent(downedTimeRemaining = 0) // Timer expired (logout counts as expiry)

                    // Get current location for downed location (they'll die here)
                    val transformComponent = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
                    tempDownedComponent.downedLocation = transformComponent?.position

                    DownedCleanupHelper.executeDeath(
                        ref,
                        commandBuffer,
                        tempDownedComponent,
                        "Logged out while downed (executing death on login)"
                    )

                    Log.finer("LoginCleanup", "Death executed - player will respawn")
                    return // Exit early - player will die and respawn
                }
                is PendingDeathTracker.RestoreAction.RestoreDowned -> {
                    // Player crashed/unloaded while downed → restore downed state
                    Log.finer("LoginCleanup", "Player crashed while downed - restoring downed state")
                    Log.finer("LoginCleanup", "  Time remaining: ${action.timeRemaining}s")
                    Log.finer("LoginCleanup", "  Downed location: ${action.downedLocation}")

                    // CRITICAL: Remove any lingering invisibility/collision components BEFORE restoring downed state
                    // If these are present, the invisibility/collision systems will think the player was already hidden/intangible
                    // and won't properly restore visibility/collision on revival
                    val hadHiddenComponent = commandBuffer.getComponent(ref, HiddenFromAdventurePlayers.getComponentType()) != null
                    if (hadHiddenComponent) {
                        commandBuffer.tryRemoveComponent(ref, HiddenFromAdventurePlayers.getComponentType())
                        Log.finer("LoginCleanup", "Removed lingering HiddenFromAdventurePlayers before restore")
                    }

                    val hadIntangibleComponent = commandBuffer.getComponent(ref, Intangible.getComponentType()) != null
                    if (hadIntangibleComponent) {
                        commandBuffer.tryRemoveComponent(ref, Intangible.getComponentType())
                        Log.finer("LoginCleanup", "Removed lingering Intangible before restore")
                    }

                    // Create fresh DownedComponent with saved timer and location
                    val restoredComponent = DownedComponent(downedTimeRemaining = action.timeRemaining)

                    // Use the saved downed location (where they were originally downed)
                    // Fallback to current location if not found in restore file
                    restoredComponent.downedLocation = action.downedLocation ?: run {
                        val transformComponent = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
                        transformComponent?.position
                    }

                    // Save current scale/displayname before systems modify them
                    val currentScale = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())?.scale ?: 1.0f
                    val currentDisplayName = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())?.clone() as? DisplayNameComponent

                    restoredComponent.originalScale = currentScale
                    restoredComponent.originalDisplayName = currentDisplayName

                    // Add DownedComponent to trigger all RefChangeSystem callbacks
                    // This will create phantom body, make invisible, disable collisions, etc.
                    commandBuffer.addComponent(ref, DownedComponent.getComponentType(), restoredComponent)

                    // Update state tracker (needed for network threads to know player is downed)
                    DownedStateTracker.setDowned(ref)

                    Log.finer("LoginCleanup", "Downed state restored - all systems triggered")

                    // Exit early - the component callbacks will handle all setup
                    return
                }
                PendingDeathTracker.RestoreAction.None -> {
                    // No action needed - continue with normal cleanup
                }
            }
        }

        // 2. ALWAYS restore scale to 1.0 on login (crash safety - no matter what)
        // NOTE: Only if player is NOT downed (downed players need tiny scale)
        val stillDowned = commandBuffer.getComponent(ref, DownedComponent.getComponentType()) != null
        if (!stillDowned) {
            val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
            if (scaleComponent != null) {
                if (scaleComponent.scale != 1.0f) {
                    Log.finer("LoginCleanup", "Found scale ${scaleComponent.scale}, restoring to 1.0 (crash safety)")
                    scaleComponent.scale = 1.0f
                    issuesFound = true
                }
            }
        }

        // 3. Ensure DisplayNameComponent exists
        val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
        if (displayNameComponent == null) {
            Log.finer("LoginCleanup", "DisplayNameComponent missing, ensuring it exists")
            commandBuffer.ensureComponent(ref, DisplayNameComponent.getComponentType())
            issuesFound = true
        }

        // 4. Ensure Interactable component exists
        val interactable = commandBuffer.getComponent(ref, Interactable.getComponentType())
        if (interactable == null) {
            Log.finer("LoginCleanup", "Interactable component missing, ensuring it exists")
            commandBuffer.ensureComponent(ref, Interactable.getComponentType())
            issuesFound = true
        }

        // 5. Remove HiddenFromAdventurePlayers if present (invisibility mode cleanup)
        val hiddenComponent = commandBuffer.getComponent(ref, HiddenFromAdventurePlayers.getComponentType())
        if (hiddenComponent != null) {
            Log.finer("LoginCleanup", "Found HiddenFromAdventurePlayers, removing")
            commandBuffer.tryRemoveComponent(ref, HiddenFromAdventurePlayers.getComponentType())
            issuesFound = true
        }

        // 6. Remove Intangible if present (collision cleanup)
        val intangibleComponent = commandBuffer.getComponent(ref, Intangible.getComponentType())
        if (intangibleComponent != null) {
            Log.finer("LoginCleanup", "Found Intangible, removing")
            commandBuffer.tryRemoveComponent(ref, Intangible.getComponentType())
            issuesFound = true
        }

        // 7. Ensure PlayerSkinComponent exists (restores default skin/outfit)
        // This ensures the player's cosmetic appearance is loaded
        commandBuffer.ensureComponent(ref, PlayerSkinComponent.getComponentType())
        val playerSkinComponent = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        if (playerSkinComponent != null) {
            Log.finer("LoginCleanup", "PlayerSkinComponent verified")
        } else {
            Log.finer("LoginCleanup", "PlayerSkinComponent could not be ensured")
            issuesFound = true
        }

        // 8. Reset camera tracking (in case player logged out with camera active in PLAYER mode)
        try {
            val playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            if (playerRefComponent != null) {
                val cameraSystem = com.hydowned.HyDownedPlugin.instance?.getCameraSystem()
                if (cameraSystem != null) {
                    // Remove from tracking set (don't call full reset since camera should already be normal)
                    cameraSystem.clearCameraTracking(playerRefComponent)
                    Log.finer("LoginCleanup", "Cleared camera tracking state")
                }
            }
        } catch (e: Exception) {
            Log.warning("LoginCleanup", "Failed to clear camera tracking: ${e.message}")
        }

        if (issuesFound) {
            Log.finer("LoginCleanup", "Fixed player state issues on login")
        } else {
            Log.finer("LoginCleanup", "Player state is clean")
        }
    }
}
