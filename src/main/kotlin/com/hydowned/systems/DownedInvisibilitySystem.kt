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
import com.hydowned.util.ComponentUtils
import com.hydowned.util.DisplayNameUtils
import com.hydowned.util.Log
import java.util.logging.Level


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
        component.originalDisplayName = DisplayNameUtils.hideNameplate(ref, commandBuffer, "Invisibility")

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

        if (Log.isEnabled(Level.FINER)) {
            Log.finer("Invisibility", "============================================")
            Log.finer("Invisibility", "Restoring player visibility")
        }

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
        ComponentUtils.refreshComponent(
            ref, commandBuffer,
            PlayerSkinComponent.getComponentType(),
            "PlayerSkinComponent",
            "Invisibility"
        )

        ComponentUtils.refreshComponent(
            ref, commandBuffer,
            ModelComponent.getComponentType(),
            "ModelComponent",
            "Invisibility"
        )

        // Restore original nameplate (replace empty DisplayNameComponent with original)
        DisplayNameUtils.restoreNameplate(ref, commandBuffer, component.originalDisplayName, "Invisibility")

        Log.finer("Invisibility", "============================================")
    }
}
