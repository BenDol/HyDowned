package com.hydowned.util

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * Utility functions for DisplayName component manipulation.
 * Used to hide and restore player nameplates.
 */
object DisplayNameUtils {

    /**
     * Hides a player's nameplate by replacing DisplayNameComponent with an empty one.
     *
     * CRITICAL: We MUST keep DisplayNameComponent present (Hytale's PlayerRemovedSystem
     * crashes if null). Strategy: Remove old one, add new empty one.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param systemName Name of the calling system (for logging)
     * @return The original DisplayNameComponent if it was stored, null otherwise
     */
    fun hideNameplate(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        systemName: String
    ): DisplayNameComponent? {
        return try {
            val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
            if (displayNameComponent != null) {
                // Clone original for restoration
                val original = displayNameComponent.clone() as DisplayNameComponent

                // Remove old DisplayNameComponent
                commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

                // Immediately add new empty DisplayNameComponent (no-args constructor = no name)
                val emptyDisplayName = DisplayNameComponent()
                commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), emptyDisplayName)

                Log.finer(systemName, "Hid nameplate (replaced with empty DisplayNameComponent)")
                original
            } else {
                Log.warning(systemName, "DisplayNameComponent not found, cannot hide nameplate")
                null
            }
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to hide nameplate: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Restores a player's original nameplate by replacing the empty DisplayNameComponent
     * with the original one.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param originalDisplayName The original DisplayNameComponent to restore
     * @param systemName Name of the calling system (for logging)
     * @return true if restoration succeeded, false otherwise
     */
    fun restoreNameplate(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        originalDisplayName: DisplayNameComponent?,
        systemName: String
    ): Boolean {
        if (originalDisplayName == null) {
            Log.warning(systemName, "No original DisplayNameComponent to restore")
            return false
        }

        return try {
            // Remove empty DisplayNameComponent
            commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

            // Add back original DisplayNameComponent
            commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)

            Log.finer(systemName, "Restored original nameplate")
            true
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to restore nameplate: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
