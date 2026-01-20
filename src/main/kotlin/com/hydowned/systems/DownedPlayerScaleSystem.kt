package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Makes downed players extremely tiny and hides their nameplate.
 *
 * This avoids client crashes while still making the player practically invisible.
 * The player is scaled down to 0.00001 (0.001% of normal size) and their nameplate is removed.
 */
class DownedPlayerScaleSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Only run if SCALE mode is enabled
        if (!config.useScaleMode) {
            Log.verbose("PlayerScale", "Skipping - invisibilityMode is not SCALE")
            return
        }

        Log.verbose("PlayerScale", "============================================")
        Log.verbose("PlayerScale", "BEFORE SCALING:")

        // Check what components exist BEFORE we do anything
        val modelBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        Log.verbose("PlayerScale", "  ModelComponent exists: ${modelBefore != null}")
        Log.verbose("PlayerScale", "  PlayerSkinComponent exists: ${skinBefore != null}")

        // Get or create EntityScaleComponent
        val scaleComponentBefore = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        Log.verbose("PlayerScale", "  EntityScaleComponent existed before: ${scaleComponentBefore != null}")

        val scaleComponent = commandBuffer.ensureAndGetComponent(ref, EntityScaleComponent.getComponentType())
        Log.verbose("PlayerScale", "  EntityScaleComponent after ensure: exists")

        // Store original scale for restoration
        component.originalScale = scaleComponent.scale
        Log.verbose("PlayerScale", "  Original scale value: ${component.originalScale}")

        // Scale down to 0.1% of normal size (practically invisible but still rendered)
        scaleComponent.scale = 0.00001f
        Log.verbose("PlayerScale", "  New scale value: ${scaleComponent.scale}")

        // Check what components exist AFTER we change scale
        val modelAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        Log.verbose("PlayerScale", "AFTER SCALING:")
        Log.verbose("PlayerScale", "  ModelComponent exists: ${modelAfter != null}")
        Log.verbose("PlayerScale", "  PlayerSkinComponent exists: ${skinAfter != null}")
        Log.verbose("PlayerScale", "============================================")

        // Hide nameplate by replacing DisplayNameComponent with empty one
        // CRITICAL: We MUST keep DisplayNameComponent present (Hytale's PlayerRemovedSystem crashes if null)
        // Strategy: Remove old one, add new empty one
        val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
        if (displayNameComponent != null) {
            // Store original for restoration
            component.originalDisplayName = displayNameComponent.clone() as DisplayNameComponent

            // Remove old DisplayNameComponent
            commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

            // Immediately add new empty DisplayNameComponent (no-args constructor = no name)
            val emptyDisplayName = DisplayNameComponent()
            commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), emptyDisplayName)

            Log.verbose("PlayerScale", "Replaced nameplate with empty DisplayNameComponent")
        } else {
            Log.warning("PlayerScale", "DisplayNameComponent not found")
        }
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Only run if SCALE mode is enabled
        if (!config.useScaleMode) {
            return
        }

        // NOTE: This callback fires for normal death/revive, but NOT during logout/entity removal
        // For logout, scale + nameplate restoration is handled in DownedCleanupHelper.cleanupDownedState()

        Log.verbose("PlayerScale", "========================d====================")
        Log.verbose("PlayerScale", "BEFORE RESTORING SCALE:")

        // Check what components exist BEFORE we restore
        val modelBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        Log.verbose("PlayerScale", "  ModelComponent exists: ${modelBefore != null}")
        Log.verbose("PlayerScale", "  PlayerSkinComponent exists: ${skinBefore != null}")

        // Restore original scale
        val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        if (scaleComponent != null) {
            Log.verbose("PlayerScale", "  Current scale: ${scaleComponent.scale}")
            Log.verbose("PlayerScale", "  Restoring to: ${component.originalScale}")
            scaleComponent.scale = component.originalScale
            Log.verbose("PlayerScale", "Player scaled back to ${component.originalScale}")
        } else {
            Log.warning("PlayerScale", "EntityScaleComponent not found, cannot restore scale")
        }

        // Check what components exist AFTER we restore
        val modelAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        Log.verbose("PlayerScale", "AFTER RESTORING SCALE:")
        Log.verbose("PlayerScale", "  ModelComponent exists: ${modelAfter != null}")
        Log.verbose("PlayerScale", "  PlayerSkinComponent exists: ${skinAfter != null}")

        // EXPERIMENTAL: Try to force a refresh of appearance components
        // Remove and re-add PlayerSkinComponent to trigger client sync
        if (skinAfter != null) {
            Log.verbose("PlayerScale", "Attempting to refresh PlayerSkinComponent...")
            try {
                val skinCopy = skinAfter.clone() as com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
                commandBuffer.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
                commandBuffer.addComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType(), skinCopy)
                Log.verbose("PlayerScale", "PlayerSkinComponent refreshed")
            } catch (e: Exception) {
                Log.error("PlayerScale", "Failed to refresh PlayerSkinComponent: ${e.message}")
                e.printStackTrace()
            }
        }

        // Try to refresh ModelComponent
        if (modelAfter != null) {
            Log.verbose("PlayerScale", "Attempting to refresh ModelComponent...")
            try {
                val modelCopy = modelAfter.clone() as com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
                commandBuffer.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
                commandBuffer.addComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(), modelCopy)
                Log.verbose("PlayerScale", "ModelComponent refreshed")
            } catch (e: Exception) {
                Log.error("PlayerScale", "Failed to refresh ModelComponent: ${e.message}")
                e.printStackTrace()
            }
        }

        Log.verbose("PlayerScale", "============================================")

        // Restore original nameplate (replace empty DisplayNameComponent with original)
        val originalDisplayName = component.originalDisplayName
        if (originalDisplayName != null) {
            try {
                // Remove empty DisplayNameComponent
                commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

                // Add back original DisplayNameComponent
                commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)

                Log.verbose("PlayerScale", "Restored original nameplate")
            } catch (e: Exception) {
                Log.warning("PlayerScale", "Failed to restore nameplate: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.warning("PlayerScale", "No original DisplayNameComponent stored")
        }
    }
}
