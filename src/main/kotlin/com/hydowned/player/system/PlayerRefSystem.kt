package com.hydowned.player.system

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.ModPlugin
import com.hydowned.logging.Log
import com.hydowned.manager.Managers

/**
 * Handles player join/leave to create/destroy ModPlayer wrappers.
 */
class PlayerRefSystem(val managers: Managers) : RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        addReason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val player = commandBuffer.getComponent(ref, Player.getComponentType())
        val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())

        if (player != null && playerRef != null) {
            managers.playerManager.add(player, playerRef)
        }
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        removeReason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
        if (playerRef == null) {
            Log.error("PlayerRefSystem", "Could not get PlayerRef on entity removal")
            return
        }

        val modPlayer = managers.playerManager.get(playerRef)
        if (modPlayer != null) {
            managers.playerManager.remove(modPlayer)
        }
    }
}
