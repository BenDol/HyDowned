package com.hydowned.player

import com.hydowned.ModPlugin
import com.hydowned.component.DownedComponent
import com.hydowned.player.aspect.OtherAspect
import com.hydowned.logging.Log
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Main event handler and system registration for HyDowned.
 *
 * Registers all ECS systems and event listeners needed for the downed mechanic.
 */
class PlayerEvents(val plugin: ModPlugin) {

    init {
        val registry = plugin.eventRegistry

        // Register global event listeners
        registry.registerGlobal(PlayerReadyEvent::class.java, this::onPlayerReady)
    }

    /**
     * Handles player ready event (after login/rejoin).
     *
     * Restores downed state if player was downed before disconnect.
     */
    fun onPlayerReady(event: PlayerReadyEvent) {
        val entityStore = event.playerRef.store
        entityStore.ensureComponent(event.playerRef, DownedComponent.getComponentType())

        val downedComponent = entityStore.getComponent(
            event.playerRef,
            DownedComponent.getComponentType()
        )

        val managers = plugin.managers

        val modPlayer = managers.playerManager.get(event.player)
        if (downedComponent != null && downedComponent.time > 0) {
            val downable = modPlayer!!.asDownable

            // Delay the down state application to ensure player is fully loaded for other clients
            val world = event.player.world
            Executors.newSingleThreadScheduledExecutor().schedule({
                world?.execute {
                    managers.downManager.down(downable, OtherAspect.create("reconnected"))
                    managers.downManager.setTime(downable, downedComponent.time)

                    Log.info("PlayerEvents",
                        "${event.player.displayName} reconnected while downed (${downedComponent.time} ticks remaining)")
                }
            }, 250, TimeUnit.MILLISECONDS)
        }
    }
}