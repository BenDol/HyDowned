package com.hydowned

import com.hydowned.commands.GiveUpCommand
import com.hydowned.components.DownedComponent
import com.hydowned.components.PhantomBodyMarker
import com.hydowned.config.DownedConfig
import com.hydowned.listeners.PlayerReadyEventListener
import com.hydowned.systems.DownedAnimationLoopSystem
import com.hydowned.systems.DownedCameraSystem
import com.hydowned.systems.DownedClearEffectsSystem
import com.hydowned.systems.DownedCollisionDisableSystem
import com.hydowned.systems.DownedDamageImmunitySystem
import com.hydowned.systems.DownedDeathInterceptor
import com.hydowned.systems.DownedHealingSuppressionSystem
import com.hydowned.systems.DownedInteractionBlockingSystem
import com.hydowned.systems.DownedInvisibilitySystem
import com.hydowned.systems.DownedLoginCleanupSystem
import com.hydowned.systems.DownedLogoutHandlerSystem
import com.hydowned.systems.DownedMovementStateOverrideSystem
import com.hydowned.systems.DownedMovementSuppressionSystem
import com.hydowned.systems.DownedPacketInterceptorSystem
import com.hydowned.systems.DownedPhantomBodySystem
import com.hydowned.systems.DownedPlayerModeSystem
import com.hydowned.systems.DownedPlayerModeSyncSystem
import com.hydowned.systems.DownedPlayerScaleSystem
import com.hydowned.systems.DownedRadiusConstraintSystem
import com.hydowned.systems.DownedRemoveInteractionsSystem
import com.hydowned.systems.DownedTimerSystem
import com.hydowned.systems.PhantomBodyAnimationSystem
import com.hydowned.systems.ReviveInteractionSystem
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.util.Log
import java.util.logging.Level

/**
 * HyDowned - Downed State Mod for Hytale
 *
 * Replaces player death with a "downed" state where players can be revived by teammates.
 *
 * Status: Death interception implemented - testing phase
 */
class HyDownedPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        var instance: HyDownedPlugin? = null
            private set
    }

    private lateinit var config: DownedConfig
    private lateinit var downedComponentType: ComponentType<EntityStore, DownedComponent>
    private lateinit var phantomBodyMarkerComponentType: ComponentType<EntityStore, PhantomBodyMarker>
    private lateinit var cameraSystem: DownedCameraSystem

    fun getDownedComponentType(): ComponentType<EntityStore, DownedComponent> {
        return downedComponentType
    }

    fun getPhantomBodyMarkerComponentType(): ComponentType<EntityStore, PhantomBodyMarker> {
        return phantomBodyMarkerComponentType
    }

    fun getCameraSystem(): DownedCameraSystem {
        return cameraSystem
    }

    override fun setup() {
        instance = this

        // Initialize configuration FIRST (we need logLevel from config)
        val pluginDataFolder = java.io.File("plugins/HyDowned")
        try {
            config = DownedConfig.load(pluginDataFolder)
        } catch (e: Exception) {
            // If config fails, use default and continue
            config = DownedConfig()
            e.printStackTrace()
        }

        // Initialize logging system with Hytale logger and config log level
        Log.init(logger, config.logLevel)
        Log.info("Plugin", "Configuration loaded - Mode: ${config.downedMode}")

        // Register components
        downedComponentType = entityStoreRegistry.registerComponent(
            DownedComponent::class.java
        ) { DownedComponent(0) }

        // Register PhantomBodyMarker component (only needed for PHANTOM mode)
        phantomBodyMarkerComponentType = entityStoreRegistry.registerComponent(
            PhantomBodyMarker::class.java
        ) { PhantomBodyMarker(null, null, null) }

        // ========== SHARED SYSTEMS (Both modes) ==========
        // Register login cleanup system (runs sanity checks + crash fix when player logs in)
        entityStoreRegistry.registerSystem(DownedLoginCleanupSystem(config))

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))

        // Damage immunity for downed players
        entityStoreRegistry.registerSystem(DownedDamageImmunitySystem(config))

        // Healing suppression for downed players
        entityStoreRegistry.registerSystem(DownedHealingSuppressionSystem(config))

        // Remove mob targeting/aggro for downed players
        // TODO: Disabled for now since we have no way of dealing with the mob AI state properly
        //entityStoreRegistry.registerSystem(DownedMobAggroSystem(config))

        // Clear effects when downed
        entityStoreRegistry.registerSystem(DownedClearEffectsSystem(config))

        // Timer system for countdown
        entityStoreRegistry.registerSystem(DownedTimerSystem(config))

        // Revive interaction system
        entityStoreRegistry.registerSystem(ReviveInteractionSystem(config))

        // Packet interceptor (blocks interactions, handles movement based on mode)
        entityStoreRegistry.registerSystem(DownedPacketInterceptorSystem(config))

        // Interaction removal and blocking
        entityStoreRegistry.registerSystem(DownedRemoveInteractionsSystem(config))
        entityStoreRegistry.registerSystem(DownedInteractionBlockingSystem(config))

        // Screen effects (camera shake, post-fx)
        //entityStoreRegistry.registerSystem(DownedScreenEffectsSystem(config))

        // Logout handler
        entityStoreRegistry.registerSystem(DownedLogoutHandlerSystem(config))

        // ========== MODE-SPECIFIC SYSTEMS ==========
        when {
            config.usePlayerMode -> {
                Log.info("Plugin", "Registering PLAYER mode systems...")

                // Player mode system (puts player into sleep state)
                entityStoreRegistry.registerSystem(DownedPlayerModeSystem(config))

                // Player mode sync (maintains sleep state)
                entityStoreRegistry.registerSystem(DownedPlayerModeSyncSystem(config))

                // Animation loop (re-sends Death animation)
                entityStoreRegistry.registerSystem(DownedAnimationLoopSystem(config))

                // Movement suppression (blocks input)
                entityStoreRegistry.registerSystem(DownedMovementSuppressionSystem(config))

                // Movement state override (sends sleeping=true every tick)
                // DISABLED - this system actually breaks the sleeping state
                // entityStoreRegistry.registerSystem(DownedMovementStateOverrideSystem(config))

                // Camera system (looks down at player)
                cameraSystem = DownedCameraSystem(config)
                entityStoreRegistry.registerSystem(cameraSystem)

                Log.info("Plugin", "PLAYER mode systems registered (6 systems)")
            }
            config.usePhantomMode -> {
                Log.info("Plugin", "Registering PHANTOM mode systems...")

                // Phantom body system
                entityStoreRegistry.registerSystem(DownedPhantomBodySystem(config))

                // Phantom body animation
                entityStoreRegistry.registerSystem(PhantomBodyAnimationSystem(config))

                // Collision disable (no player-to-player collision)
                entityStoreRegistry.registerSystem(DownedCollisionDisableSystem(config))

                // Radius constraint (keeps player near body)
                entityStoreRegistry.registerSystem(DownedRadiusConstraintSystem(config))

                // Mode-specific invisibility systems
                if (config.useScaleMode) {
                    entityStoreRegistry.registerSystem(DownedPlayerScaleSystem(config))
                    Log.info("Plugin", "  - SCALE mode enabled")
                }
                if (config.useInvisibleMode) {
                    entityStoreRegistry.registerSystem(DownedInvisibilitySystem(config))
                    Log.info("Plugin", "  - INVISIBLE mode enabled")
                }

                val phantomSystemCount = 4 + (if (config.useScaleMode) 1 else 0) + (if (config.useInvisibleMode) 1 else 0)
                Log.info("Plugin", "PHANTOM mode systems registered ($phantomSystemCount systems)")
            }
        }

        // Register command
        commandRegistry.registerCommand(GiveUpCommand(config))

        // Register event listeners
        val playerReadyListener = PlayerReadyEventListener(config)
        eventRegistry.registerGlobal(PlayerReadyEvent::class.java, playerReadyListener::onPlayerReady)

        Log.info("Plugin", "HyDowned setup complete - Mode: ${config.downedMode}")
    }

    override fun start() {
        Log.separator("Plugin", Level.FINER)
        Log.finer("Plugin", "Start Phase")
        Log.separator("Plugin", Level.FINER)
        Log.finer("Plugin", "")

        Log.separator("Plugin", Level.FINER)
        Log.finer("Plugin", "HyDowned plugin started successfully!")
        Log.finer("Plugin", "Downed Mode: ${config.downedMode}")
        Log.finer("Plugin", "Invisibility Mode: ${config.invisibilityMode}")
        Log.separator("Plugin", Level.FINER)
        Log.finer("Plugin", "FEATURES:")

        if (config.usePlayerMode) {
            Log.finer("Plugin", "PLAYER MODE:")
            Log.finer("Plugin", "  ✓ Player body stays in place (lays down)")
            Log.finer("Plugin", "  ✓ Death animation plays on player")
            Log.finer("Plugin", "  ✓ Camera looks down from above")
            Log.finer("Plugin", "  ✓ Screen effects: camera shake + darkened vision")
            Log.finer("Plugin", "  ✓ Movement locked (player in sleep state)")
        } else if (config.usePhantomMode) {
            Log.finer("Plugin", "PHANTOM MODE:")
            Log.finer("Plugin", "  ✓ Phantom body spawned at downed location (visible to all)")
            Log.finer("Plugin", "  ✓ Death animation plays on phantom body")

            if (config.useScaleMode) {
                Log.finer("Plugin", "  ✓ Downed player: SCALE mode (0.01% size, nameplate hidden)")
            } else if (config.useInvisibleMode) {
                Log.finer("Plugin", "  ✓ Downed player: INVISIBLE mode (VisibilityComponent, nameplate hidden)")
            }

            Log.finer("Plugin", "  ✓ Character collision disabled (no pushing/attacking players)")
            Log.finer("Plugin", "  ✓ Block collision enabled (can't walk through walls)")
            Log.finer("Plugin", "  ✓ 7 block movement radius from phantom body")
            Log.finer("Plugin", "  ✓ Player teleports back to body on revive")
        }

        Log.finer("Plugin", "")
        Log.finer("Plugin", "GENERAL:")
        Log.finer("Plugin", "  ✓ Interactions blocked while downed")
        Log.finer("Plugin", "  ✓ Immune to damage while downed")
        Log.finer("Plugin", "  ✓ Healing blocked while downed")
        Log.finer("Plugin", "  ✓ Mobs lose aggro and cannot target downed players")
        Log.finer("Plugin", "  ✓ All active effects cleared when downed")
        Log.finer("Plugin", "  ✓ Timer: ${config.downedTimerSeconds}s until death")
        Log.finer("Plugin", "  ✓ Revive: CROUCH near body (${config.reviveRange} blocks)")
        Log.finer("Plugin", "  ✓ Logout while downed: auto-death + visibility restoration")
        Log.finer("Plugin", "  ✓ Command: /giveup to instantly die while downed")
        Log.separator("Plugin", Level.FINER)
    }

    override fun shutdown() {
        Log.finer("Plugin", "Shutting down...")
        instance = null
        Log.finer("Plugin", "Shutdown complete")
    }
}
