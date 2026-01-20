package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Makes downed players invisible to all other players.
 *
 * When a player becomes downed:
 * - Iterate through all online players
 * - Add the downed player's UUID to each player's HiddenPlayersManager
 * - The downed player becomes invisible (only phantom body is visible)
 *
 * When a player is revived:
 * - Iterate through all online players
 * - Remove the revived player's UUID from each player's HiddenPlayersManager
 * - The player becomes visible again
 */
class DownedPlayerVisibilitySystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType(),
        TransformComponent.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get the UUID of the downed player
        val downedUuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
            ?: return
        val downedUuid = downedUuidComponent.uuid

        println("[HyDowned] [Visibility] Hiding downed player from all other players: $downedUuid")

        // Hide this player from all other players
        val allPlayers = Universe.get().players
        for (player in allPlayers) {
            val playerEntityRef = player.reference ?: continue

            // Get the PlayerRef component which has the HiddenPlayersManager
            val playerRefComponent = store.getComponent(playerEntityRef, PlayerRef.getComponentType())
                ?: continue

            // Hide the downed player
            playerRefComponent.getHiddenPlayersManager().hidePlayer(downedUuid)
        }

        println("[HyDowned] [Visibility] ✓ Downed player hidden from ${allPlayers.size} players")
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
        // Get the UUID of the revived player
        val revivedUuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
            ?: return
        val revivedUuid = revivedUuidComponent.uuid

        println("[HyDowned] [Visibility] Showing revived player to all other players: $revivedUuid")

        // Show this player to all other players
        val allPlayers = Universe.get().players
        for (player in allPlayers) {
            val playerEntityRef = player.reference ?: continue

            // Get the PlayerRef component which has the HiddenPlayersManager
            val playerRefComponent = store.getComponent(playerEntityRef, PlayerRef.getComponentType())
                ?: continue

            // Show the revived player
            playerRefComponent.hiddenPlayersManager.showPlayer(revivedUuid)
        }

        println("[HyDowned] [Visibility] ✓ Revived player shown to ${allPlayers.size} players")
    }
}
