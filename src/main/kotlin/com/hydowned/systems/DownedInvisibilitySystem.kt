package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Makes downed players completely invisible (pure invisibility mode).
 *
 * Uses HiddenFromAdventurePlayers to hide the player model entirely.
 * Also hides the nameplate by replacing DisplayNameComponent with an empty one.
 */
class DownedInvisibilitySystem(
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
        // Only run if INVISIBLE mode is enabled
        if (!config.useInvisibleMode) {
            println("[HyDowned] [Invisible] Skipping - invisibilityMode is not INVISIBLE")
            return
        }

        println("[HyDowned] [Invisible] ============================================")
        println("[HyDowned] [Invisible] Making player invisible")

        // Make player invisible using HiddenFromAdventurePlayers
        try {
            // Check if player was already hidden
            val alreadyHidden = commandBuffer.getComponent(ref, HiddenFromAdventurePlayers.getComponentType()) != null
            component.wasVisibleBefore = !alreadyHidden

            if (!alreadyHidden) {
                commandBuffer.addComponent(ref, HiddenFromAdventurePlayers.getComponentType(), HiddenFromAdventurePlayers.INSTANCE)
                println("[HyDowned] [Invisible] ✓ Added HiddenFromAdventurePlayers component")
            } else {
                println("[HyDowned] [Invisible] ✓ Player already hidden")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Invisible] ⚠ Failed to add HiddenFromAdventurePlayers: ${e.message}")
            e.printStackTrace()
        }

        // Hide nameplate by replacing DisplayNameComponent with empty one
        val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
        if (displayNameComponent != null) {
            // Store original for restoration
            component.originalDisplayName = displayNameComponent.clone() as DisplayNameComponent

            // Remove old DisplayNameComponent
            commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

            // Immediately add new empty DisplayNameComponent (no-args constructor = no name)
            val emptyDisplayName = DisplayNameComponent()
            commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), emptyDisplayName)

            println("[HyDowned] [Invisible] ✓ Hid nameplate")
        } else {
            println("[HyDowned] [Invisible] ⚠ DisplayNameComponent not found")
        }

        println("[HyDowned] [Invisible] ============================================")
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
        // Only run if INVISIBLE mode is enabled
        if (!config.useInvisibleMode) {
            return
        }

        println("[HyDowned] [Invisible] ============================================")
        println("[HyDowned] [Invisible] Restoring player visibility")

        // Restore visibility by removing HiddenFromAdventurePlayers (if we added it)
        try {
            if (component.wasVisibleBefore) {
                commandBuffer.tryRemoveComponent(ref, HiddenFromAdventurePlayers.getComponentType())
                println("[HyDowned] [Invisible] ✓ Removed HiddenFromAdventurePlayers component (visibility restored)")
            } else {
                println("[HyDowned] [Invisible] ✓ Player remains hidden (was already hidden)")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Invisible] ⚠ Failed to remove HiddenFromAdventurePlayers: ${e.message}")
            e.printStackTrace()
        }

        // Component refresh to force client to re-sync visibility state
        // This fixes the issue where player stays invisible after revival (similar to skin loss bug)
        try {
            val skinComponent = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
            if (skinComponent != null) {
                val skinCopy = skinComponent.clone() as PlayerSkinComponent
                commandBuffer.removeComponent(ref, PlayerSkinComponent.getComponentType())
                commandBuffer.addComponent(ref, PlayerSkinComponent.getComponentType(), skinCopy)
                println("[HyDowned] [Invisible] ✓ Refreshed PlayerSkinComponent (client re-sync)")
            }

            val modelComponent = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
            if (modelComponent != null) {
                val modelCopy = modelComponent.clone() as ModelComponent
                commandBuffer.removeComponent(ref, ModelComponent.getComponentType())
                commandBuffer.addComponent(ref, ModelComponent.getComponentType(), modelCopy)
                println("[HyDowned] [Invisible] ✓ Refreshed ModelComponent (client re-sync)")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Invisible] ⚠ Failed to refresh skin/model components: ${e.message}")
            e.printStackTrace()
        }

        // Restore original nameplate (replace empty DisplayNameComponent with original)
        val originalDisplayName = component.originalDisplayName
        if (originalDisplayName != null) {
            try {
                // Remove empty DisplayNameComponent
                commandBuffer.removeComponent(ref, DisplayNameComponent.getComponentType())

                // Add back original DisplayNameComponent
                commandBuffer.addComponent(ref, DisplayNameComponent.getComponentType(), originalDisplayName)

                println("[HyDowned] [Invisible] ✓ Restored original nameplate")
            } catch (e: Exception) {
                println("[HyDowned] [Invisible] ⚠ Failed to restore nameplate: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("[HyDowned] [Invisible] ⚠ No original DisplayNameComponent stored")
        }

        println("[HyDowned] [Invisible] ============================================")
    }
}
