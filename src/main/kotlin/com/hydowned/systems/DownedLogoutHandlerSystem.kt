package com.hydowned.systems

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.DownedCleanupHelper

/**
 * Handles when a downed player logs out (entity is removed from world).
 *
 * When a player with DownedComponent is being removed, executes death using
 * the centralized DownedCleanupHelper to ensure:
 * - Player dies at their phantom body location
 * - Player is no longer marked as invisible
 * - Phantom body is cleaned up
 * - Player will respawn normally when they log back in
 */
class DownedLogoutHandlerSystem(
    private val config: DownedConfig
) : RefSystem<EntityStore>() {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed - we only care about entity removal
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get player UUID for logging
        val uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
        val playerUuid = uuidComponent?.uuid

        println("[HyDowned] [LogoutHandler] Downed player logging out: $playerUuid")
        println("[HyDowned] [LogoutHandler] Reason: $reason")

        // Get the DownedComponent
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
            ?: return

        println("[HyDowned] [LogoutHandler] Player quit while downed")

        // Use centralized cleanup helper to handle death
        // forceHealthToZero=true because damage during entity removal doesn't save death state properly
        DownedCleanupHelper.executeDeath(
            ref,
            commandBuffer,
            downedComponent,
            "Player logout (reason: $reason)",
            forceHealthToZero = true
        )

        println("[HyDowned] [LogoutHandler] ✓ Logout cleanup complete")
    }
}
