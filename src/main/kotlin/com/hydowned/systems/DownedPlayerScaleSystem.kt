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
import com.hydowned.util.ComponentUtils
import com.hydowned.util.DisplayNameUtils
import com.hydowned.util.Log
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent


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
        // Only run in PHANTOM mode with SCALE invisibility
        if (!config.usePhantomMode || !config.useScaleMode) {
            Log.finer("PlayerScale", "Skipping - not PHANTOM mode or not SCALE invisibility")
            return
        }

        Log.finer("PlayerScale", "============================================")
        Log.finer("PlayerScale", "BEFORE SCALING:")

        // Check what components exist BEFORE we do anything
        val modelBefore = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        Log.finer("PlayerScale", "  ModelComponent exists: ${modelBefore != null}")
        Log.finer("PlayerScale", "  PlayerSkinComponent exists: ${skinBefore != null}")

        // Get or create EntityScaleComponent
        val scaleComponentBefore = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        Log.finer("PlayerScale", "  EntityScaleComponent existed before: ${scaleComponentBefore != null}")

        val scaleComponent = commandBuffer.ensureAndGetComponent(ref, EntityScaleComponent.getComponentType())
        Log.finer("PlayerScale", "  EntityScaleComponent after ensure: exists")

        // Store original scale for restoration
        component.originalScale = scaleComponent.scale
        Log.finer("PlayerScale", "  Original scale value: ${component.originalScale}")

        // Scale down to 0.1% of normal size (practically invisible but still rendered)
        scaleComponent.scale = 0.00001f
        Log.finer("PlayerScale", "  New scale value: ${scaleComponent.scale}")

        // Check what components exist AFTER we change scale
        val modelAfter = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        Log.finer("PlayerScale", "AFTER SCALING:")
        Log.finer("PlayerScale", "  ModelComponent exists: ${modelAfter != null}")
        Log.finer("PlayerScale", "  PlayerSkinComponent exists: ${skinAfter != null}")
        Log.finer("PlayerScale", "============================================")

        // Hide nameplate by replacing DisplayNameComponent with empty one
        component.originalDisplayName = DisplayNameUtils.hideNameplate(ref, commandBuffer, "PlayerScale")
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
        // Only run in PHANTOM mode with SCALE invisibility
        if (!config.usePhantomMode || !config.useScaleMode) {
            return
        }

        // NOTE: This callback fires for normal death/revive, but NOT during logout/entity removal
        // For logout, scale + nameplate restoration is handled in DownedCleanupHelper.cleanupDownedState()

        Log.finer("PlayerScale", "========================d====================")
        Log.finer("PlayerScale", "BEFORE RESTORING SCALE:")

        // Check what components exist BEFORE we restore
        val modelBefore = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
        val skinBefore = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        Log.finer("PlayerScale", "  ModelComponent exists: ${modelBefore != null}")
        Log.finer("PlayerScale", "  PlayerSkinComponent exists: ${skinBefore != null}")

        // Restore original scale
        ComponentUtils.restoreComponentProperty(
            ref, commandBuffer,
            EntityScaleComponent.getComponentType(),
            "scale",
            "PlayerScale"
        ) { scaleComponent ->
            Log.finer("PlayerScale", "  Current scale: ${scaleComponent.scale}")
            Log.finer("PlayerScale", "  Restoring to: ${component.originalScale}")
            scaleComponent.scale = component.originalScale
        }

        // Check what components exist AFTER we restore
        val modelAfter = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
        val skinAfter = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        Log.finer("PlayerScale", "AFTER RESTORING SCALE:")
        Log.finer("PlayerScale", "  ModelComponent exists: ${modelAfter != null}")
        Log.finer("PlayerScale", "  PlayerSkinComponent exists: ${skinAfter != null}")

        // EXPERIMENTAL: Try to force a refresh of appearance components
        // Remove and re-add PlayerSkinComponent to trigger client sync
        ComponentUtils.refreshComponent(
            ref, commandBuffer,
            PlayerSkinComponent.getComponentType(),
            "PlayerSkinComponent",
            "PlayerScale"
        )

        // Try to refresh ModelComponent
        ComponentUtils.refreshComponent(
            ref, commandBuffer,
            ModelComponent.getComponentType(),
            "ModelComponent",
            "PlayerScale"
        )

        Log.finer("PlayerScale", "============================================")

        // Restore original nameplate (replace empty DisplayNameComponent with original)
        DisplayNameUtils.restoreNameplate(ref, commandBuffer, component.originalDisplayName, "PlayerScale")
    }
}
