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
            println("[HyDowned] [Scale] Skipping - invisibilityMode is not SCALE")
            return
        }

        println("[HyDowned] [Scale] ============================================")
        println("[HyDowned] [Scale] BEFORE SCALING:")

        // Check what components exist BEFORE we do anything
        val modelBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        println("[HyDowned] [Scale]   ModelComponent exists: ${modelBefore != null}")
        println("[HyDowned] [Scale]   PlayerSkinComponent exists: ${skinBefore != null}")

        // Get or create EntityScaleComponent
        val scaleComponentBefore = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        println("[HyDowned] [Scale]   EntityScaleComponent existed before: ${scaleComponentBefore != null}")

        val scaleComponent = commandBuffer.ensureAndGetComponent(ref, EntityScaleComponent.getComponentType())
        println("[HyDowned] [Scale]   EntityScaleComponent after ensure: exists")

        // Store original scale for restoration
        component.originalScale = scaleComponent.scale
        println("[HyDowned] [Scale]   Original scale value: ${component.originalScale}")

        // Scale down to 0.1% of normal size (practically invisible but still rendered)
        scaleComponent.scale = 0.00001f
        println("[HyDowned] [Scale]   New scale value: ${scaleComponent.scale}")

        // Check what components exist AFTER we change scale
        val modelAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        println("[HyDowned] [Scale] AFTER SCALING:")
        println("[HyDowned] [Scale]   ModelComponent exists: ${modelAfter != null}")
        println("[HyDowned] [Scale]   PlayerSkinComponent exists: ${skinAfter != null}")
        println("[HyDowned] [Scale] ============================================")

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

            println("[HyDowned] [Scale] ✓ Replaced nameplate with empty DisplayNameComponent")
        } else {
            println("[HyDowned] [Scale] ⚠ DisplayNameComponent not found")
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

        println("[HyDowned] [Scale] ============================================")
        println("[HyDowned] [Scale] BEFORE RESTORING SCALE:")

        // Check what components exist BEFORE we restore
        val modelBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        println("[HyDowned] [Scale]   ModelComponent exists: ${modelBefore != null}")
        println("[HyDowned] [Scale]   PlayerSkinComponent exists: ${skinBefore != null}")

        // Restore original scale
        val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        if (scaleComponent != null) {
            println("[HyDowned] [Scale]   Current scale: ${scaleComponent.scale}")
            println("[HyDowned] [Scale]   Restoring to: ${component.originalScale}")
            scaleComponent.scale = component.originalScale
            println("[HyDowned] [Scale] ✓ Player scaled back to ${component.originalScale}")
        } else {
            println("[HyDowned] [Scale] ⚠ EntityScaleComponent not found, cannot restore scale")
        }

        // Check what components exist AFTER we restore
        val modelAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        println("[HyDowned] [Scale] AFTER RESTORING SCALE:")
        println("[HyDowned] [Scale]   ModelComponent exists: ${modelAfter != null}")
        println("[HyDowned] [Scale]   PlayerSkinComponent exists: ${skinAfter != null}")

        // EXPERIMENTAL: Try to force a refresh of appearance components
        // Remove and re-add PlayerSkinComponent to trigger client sync
        if (skinAfter != null) {
            println("[HyDowned] [Scale] Attempting to refresh PlayerSkinComponent...")
            try {
                val skinCopy = skinAfter.clone() as com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
                commandBuffer.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
                commandBuffer.addComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType(), skinCopy)
                println("[HyDowned] [Scale] ✓ PlayerSkinComponent refreshed")
            } catch (e: Exception) {
                println("[HyDowned] [Scale] ✗ Failed to refresh PlayerSkinComponent: ${e.message}")
                e.printStackTrace()
            }
        }

        // Try to refresh ModelComponent
        if (modelAfter != null) {
            println("[HyDowned] [Scale] Attempting to refresh ModelComponent...")
            try {
                val modelCopy = modelAfter.clone() as com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
                commandBuffer.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType())
                commandBuffer.addComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(), modelCopy)
                println("[HyDowned] [Scale] ✓ ModelComponent refreshed")
            } catch (e: Exception) {
                println("[HyDowned] [Scale] ✗ Failed to refresh ModelComponent: ${e.message}")
                e.printStackTrace()
            }
        }

        println("[HyDowned] [Scale] ============================================")

        // Restore original nameplate (replace empty DisplayNameComponent with original)
        val originalDisplayName = component.originalDisplayName
        if (originalDisplayName != null) {
            try {
                // Remove empty DisplayNameComponent
                commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

                // Add back original DisplayNameComponent
                commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)

                println("[HyDowned] [Scale] ✓ Restored original nameplate")
            } catch (e: Exception) {
                println("[HyDowned] [Scale] ⚠ Failed to restore nameplate: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("[HyDowned] [Scale] ⚠ No original DisplayNameComponent stored")
        }
    }
}
