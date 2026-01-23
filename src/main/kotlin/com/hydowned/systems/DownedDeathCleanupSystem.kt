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
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId

/**
 * Handles cleanup when a downed player dies and respawns.
 *
 * This system handles the case where a downed player takes enough damage to die
 * (when allowedDownedDamage is configured to allow certain damage types).
 *
 * When DeathComponent is added (player died):
 * - Keeps player in sleep/death animation state during death screen
 * - Removes phantom body (so it doesn't show on death screen)
 * - Keeps DownedComponent until respawn
 *
 * When DeathComponent is removed (player respawned):
 * - Cleans up all downed state (sleep animation, movement states, visibility, etc.)
 * - Removes DownedComponent
 * - Player stands up normally after respawn
 *
 * This is a RefChangeSystem that triggers at the EXACT moment death/respawn happens.
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
        // This means they died while downed
        // DON'T clean up yet - keep them in sleep/death animation until respawn
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
        if (downedComponent == null) {
            return
        }

        // Only clean up phantom body if it exists (so it doesn't appear on death screen)
        val phantomBodyRef = downedComponent.phantomBodyRef
        if (phantomBodyRef != null && phantomBodyRef.isValid) {
            try {
                val phantomNetworkId = commandBuffer.getComponent(phantomBodyRef, NetworkId.getComponentType())
                commandBuffer.removeEntity(phantomBodyRef, com.hypixel.hytale.component.RemoveReason.UNLOAD)

                if (phantomNetworkId != null) {
                    com.hydowned.network.DownedStateTracker.removePhantomBody(phantomNetworkId.id)
                }
            } catch (e: Exception) {
                Log.warning("DeathCleanup", "Failed to remove phantom body: ${e.message}")
            }
        }
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        deathComponent: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // DeathComponent removed means player respawned
        // NOW we clean up the downed state (sleep animation, movement states, etc.)
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
        if (downedComponent == null) {
            return
        }

        // Clean up all downed state and remove DownedComponent
        DownedCleanupHelper.cleanupForDeath(ref, commandBuffer, downedComponent)
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
