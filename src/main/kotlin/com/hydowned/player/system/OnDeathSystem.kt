package com.hydowned.player.system

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.ModPlugin
import com.hydowned.logging.Log
import com.hydowned.manager.Managers

/**
 * Handles cleanup when a player actually dies (DeathComponent added).
 *
 * This system listens for DeathComponent being added to players and ensures
 * that if they were downed, their downed state is properly cleaned up before
 * respawning.
 */
class OnDeathSystem(val managers: Managers) : DeathSystems.OnDeathSystem() {

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        deathComponent: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        val revivePlayer = managers.playerManager.get(player) ?: return
        val downable = revivePlayer.asDownable

        // If player was downed when they died, clean up the downed state
        if (downable.isDowned()) {
            Log.finer("OnDeathSystem",
                "${player.displayName} died while downed - cleaning up downed state")
            managers.downManager.onDeath(downable)
        }
    }
}
