package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.DownedCleanupHelper
import com.hydowned.util.Log

/**
 * Detects when a downed player actually dies (DeathComponent is added).
 *
 * This system handles the case where a downed player takes enough damage to die
 * (when allowedDownedDamage is configured to allow certain damage types).
 *
 * When DeathComponent is added to a player with DownedComponent, this system:
 * 1. Restores all components (DisplayName, visibility, collision, etc.)
 * 2. Removes DownedComponent so they respawn normally
 * 3. Cleans up phantom body
 *
 * This is a RefChangeSystem that triggers at the EXACT moment death happens
 * (no race conditions like health polling).
 */
class DownedDeathCleanupSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DeathComponent>() {

    // Query for players with BOTH DownedComponent and DeathComponent
    // This catches downed players who just died
    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        DeathComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun componentType() = DeathComponent.getComponentType()

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        deathComponent: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // DeathComponent was just added to a downed player
        // This means they died while downed - clean up DownedComponent
        val playerComponent = commandBuffer.getComponent(ref, Player.getComponentType())
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())

        if (downedComponent == null) {
            return
        }

        Log.warning("DeathCleanup", "Downed player ${playerComponent?.displayName} died (DeathComponent added) - cleaning up DownedComponent")

        // Use centralized cleanup helper
        DownedCleanupHelper.cleanupForDeath(ref, commandBuffer, downedComponent)
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        deathComponent: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - we only care about DeathComponent being added
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DeathComponent?,
        newComponent: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - we only care about DeathComponent being added
    }
}
