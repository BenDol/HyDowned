package com.hydowned.util

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * Utility functions for common component operations across systems.
 * Reduces code duplication in RefChangeSystem implementations.
 */
object ComponentUtils {

    /**
     * Safely restores a component property value with proper error handling and logging.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component access
     * @param componentType Type of component to restore
     * @param propertyName Name of property being restored (for logging)
     * @param systemName Name of the calling system (for logging)
     * @param restoreAction Lambda that performs the actual restoration
     * @return true if restoration succeeded, false otherwise
     */
    inline fun <reified T : Component<EntityStore>> restoreComponentProperty(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        componentType: ComponentType<EntityStore, T>,
        propertyName: String,
        systemName: String,
        restoreAction: (T) -> Unit
    ): Boolean {
        return try {
            val component = commandBuffer.getComponent(ref, componentType)
            if (component != null) {
                restoreAction(component)
                Log.finer(systemName, "Restored $propertyName")
                true
            } else {
                Log.warning(systemName,
                    "${componentType.javaClass.simpleName} not found, cannot restore $propertyName")
                false
            }
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to restore $propertyName: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Refreshes a component by cloning, removing, and re-adding it.
     * This forces the client to re-sync the component state.
     *
     * Commonly used for ModelComponent and PlayerSkinComponent to fix
     * visibility/appearance issues after state changes.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param componentType Type of component to refresh
     * @param componentName Name of component (for logging)
     * @param systemName Name of the calling system (for logging)
     * @return true if refresh succeeded, false otherwise
     */
    inline fun <reified T : Component<EntityStore>> refreshComponent(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        componentType: ComponentType<EntityStore, T>,
        componentName: String,
        systemName: String
    ): Boolean {
        return try {
            val component = commandBuffer.getComponent(ref, componentType)
            if (component != null) {
                val componentCopy = component.clone() as T
                commandBuffer.removeComponent(ref, componentType)
                commandBuffer.addComponent(ref, componentType, componentCopy)
                Log.finer(systemName, "Refreshed $componentName (client re-sync)")
                true
            } else {
                Log.finer(systemName, "$componentName not found, skipping refresh")
                false
            }
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to refresh $componentName: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Adds a component if it wasn't already present, tracking the original state.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param componentType Type of component to add
     * @param componentName Name of component (for logging)
     * @param systemName Name of the calling system (for logging)
     * @return true if component was added (wasn't present before), false if it already existed
     */
    fun <T : Component<EntityStore>> addComponentIfMissing(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        componentType: ComponentType<EntityStore, T>,
        componentName: String,
        systemName: String
    ): Boolean {
        return try {
            val alreadyPresent = commandBuffer.getComponent(ref, componentType) != null
            if (!alreadyPresent) {
                commandBuffer.ensureComponent(ref, componentType)
                Log.finer(systemName, "Added $componentName component")
                true
            } else {
                Log.finer(systemName, "$componentName component already present")
                false
            }
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to add $componentName: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes a component with proper error handling and logging.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param componentType Type of component to remove
     * @param componentName Name of component (for logging)
     * @param systemName Name of the calling system (for logging)
     * @return true if removal succeeded, false otherwise
     */
    fun <T : Component<EntityStore>> removeComponentSafely(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        componentType: ComponentType<EntityStore, T>,
        componentName: String,
        systemName: String
    ): Boolean {
        return try {
            commandBuffer.tryRemoveComponent(ref, componentType)
            Log.finer(systemName, "Removed $componentName component")
            true
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to remove $componentName: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Ensures a component exists with proper error handling and logging.
     *
     * @param ref Entity reference
     * @param commandBuffer Command buffer for component manipulation
     * @param componentType Type of component to ensure
     * @param componentName Name of component (for logging)
     * @param systemName Name of the calling system (for logging)
     * @return true if operation succeeded, false otherwise
     */
    fun <T : Component<EntityStore>> ensureComponentSafely(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        componentType: ComponentType<EntityStore, T>,
        componentName: String,
        systemName: String
    ): Boolean {
        return try {
            commandBuffer.ensureComponent(ref, componentType)
            Log.finer(systemName, "Ensured $componentName component")
            true
        } catch (e: Exception) {
            Log.warning(systemName, "Failed to ensure $componentName: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
