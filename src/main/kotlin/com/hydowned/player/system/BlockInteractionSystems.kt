package com.hydowned.player.system

import com.hydowned.aspect.Downable
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabDirtySystems
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.manager.Managers

/**
 * Prevents downed players from breaking blocks.
 */
class BlockBreakSystem(private val managers: Managers) : PrefabDirtySystems.BlockBreakDirtySystem() {

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        val downable = Downable.find(index, archetypeChunk, managers.playerManager) ?: return

        // Cancel block breaking if player is downed
        if (downable.isDowned()) {
            event.isCancelled = true
        }
    }
}

/**
 * Prevents downed players from placing blocks.
 */
class BlockPlaceSystem(private val managers: Managers) : PrefabDirtySystems.BlockPlaceDirtySystem() {

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent
    ) {
        val downable = Downable.find(index, archetypeChunk, managers.playerManager) ?: return

        // Cancel block placement if player is downed
        if (downable.isDowned()) {
            event.isCancelled = true
        }
    }
}
