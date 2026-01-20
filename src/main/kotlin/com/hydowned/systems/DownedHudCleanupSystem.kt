package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.protocol.packets.interface_.HideEventTitle
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Cleans up the event title HUD when a player exits the downed state.
 *
 * This system listens for when DownedComponent is removed (revive or death)
 * and hides the event title showing death timer and revive progress.
 */
class DownedHudCleanupSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType(),
        PlayerRef.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun componentType(): com.hypixel.hytale.component.ComponentType<EntityStore, DownedComponent> {
        return DownedComponent.getComponentType()
    }

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Do nothing when downed - HudSystem handles showing
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Do nothing on update
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Player was revived or died - clean up event title HUD
        val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            ?: return

        println("[HyDowned] Cleaning up event title HUD for exiting downed state")

        // Hide event title
        try {
            val hideTitle = HideEventTitle()
            playerRef.packetHandler.write(hideTitle)
            Log.verbose("HudCleanup", "Event title hidden")
        } catch (e: Exception) {
            println("[HyDowned] Error hiding event title: ${e.message}")
        }
    }
}
