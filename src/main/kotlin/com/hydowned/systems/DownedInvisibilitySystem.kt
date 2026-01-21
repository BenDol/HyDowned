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
import com.hydowned.util.Log


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
        // Only run in PHANTOM mode with INVISIBLE invisibility
        if (!config.usePhantomMode || !config.useInvisibleMode) {
            Log.finer("Invisibility", "Skipping - not PHANTOM mode or not INVISIBLE invisibility")
            return
        }

        Log.finer("Invisibility", "============================================")
        Log.finer("Invisibility", "Making player invisible")

        // Make player invisible using HiddenFromAdventurePlayers
        try {
            // Check if player was already hidden
            val alreadyHidden = commandBuffer.getComponent(ref, HiddenFromAdventurePlayers.getComponentType()) != null
            component.wasVisibleBefore = !alreadyHidden

            if (!alreadyHidden) {
                commandBuffer.addComponent(ref, HiddenFromAdventurePlayers.getComponentType(), HiddenFromAdventurePlayers.INSTANCE)
                Log.finer("Invisibility", "Added HiddenFromAdventurePlayers component")
            } else {
                Log.finer("Invisibility", "Player already hidden")
            }
        } catch (e: Exception) {
            Log.warning("Invisibility", "Failed to add HiddenFromAdventurePlayers: ${e.message}")
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

            Log.finer("Invisibility", "Hid nameplate")
        } else {
            Log.warning("Invisibility", "DisplayNameComponent not found")
        }

        Log.finer("Invisibility", "============================================")
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
        // Only run in PHANTOM mode with INVISIBLE invisibility
        if (!config.usePhantomMode || !config.useInvisibleMode) {
            return
        }

        Log.finer("Invisibility", "============================================")
        Log.finer("Invisibility", "Restoring player visibility")

        // Restore visibility by removing HiddenFromAdventurePlayers (if we added it)
        try {
            if (component.wasVisibleBefore) {
                commandBuffer.tryRemoveComponent(ref, HiddenFromAdventurePlayers.getComponentType())
                Log.finer("Invisibility", "Removed HiddenFromAdventurePlayers component (visibility restored)")
            } else {
                Log.finer("Invisibility", "Player remains hidden (was already hidden)")
            }
        } catch (e: Exception) {
            Log.warning("Invisibility", "Failed to remove HiddenFromAdventurePlayers: ${e.message}")
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
                Log.finer("Invisibility", "Refreshed PlayerSkinComponent (client re-sync)")
            }

            val modelComponent = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
            if (modelComponent != null) {
                val modelCopy = modelComponent.clone() as ModelComponent
                commandBuffer.removeComponent(ref, ModelComponent.getComponentType())
                commandBuffer.addComponent(ref, ModelComponent.getComponentType(), modelCopy)
                Log.finer("Invisibility", "Refreshed ModelComponent (client re-sync)")
            }
        } catch (e: Exception) {
            Log.warning("Invisibility", "Failed to refresh skin/model components: ${e.message}")
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

                Log.finer("Invisibility", "Restored original nameplate")
            } catch (e: Exception) {
                Log.warning("Invisibility", "Failed to restore nameplate: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.warning("Invisibility", "No original DisplayNameComponent stored")
        }

        Log.finer("Invisibility", "============================================")
    }
}
