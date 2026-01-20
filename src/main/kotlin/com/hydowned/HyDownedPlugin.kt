package com.hydowned

import com.hydowned.commands.GiveUpCommand
import com.hydowned.components.DownedComponent
import com.hydowned.components.PhantomBodyMarker
import com.hydowned.config.DownedConfig
import com.hydowned.listeners.PlayerReadyEventListener
import com.hydowned.systems.DownedClearEffectsSystem
import com.hydowned.systems.DownedCollisionDisableSystem
import com.hydowned.systems.DownedDamageImmunitySystem
import com.hydowned.systems.DownedDeathInterceptor
import com.hydowned.systems.DownedDisableItemsSystem
import com.hydowned.systems.DownedHealingSuppressionSystem
import com.hydowned.systems.DownedInteractionBlockingSystem
import com.hydowned.systems.DownedInvisibilitySystem
import com.hydowned.systems.DownedLoginCleanupSystem
import com.hydowned.systems.DownedLogoutHandlerSystem
import com.hydowned.systems.DownedPacketInterceptorSystem
import com.hydowned.systems.DownedPhantomBodySystem
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

    fun getDownedComponentType(): ComponentType<EntityStore, DownedComponent> {
        return downedComponentType
    }

    fun getPhantomBodyMarkerComponentType(): ComponentType<EntityStore, PhantomBodyMarker> {
        return phantomBodyMarkerComponentType
    }

    override fun setup() {
        Log.separator("Plugin")
        Log.verbose("Plugin", "Setup Phase")
        Log.separator("Plugin")

        instance = this

        // Initialize configuration
        val pluginDataFolder = java.io.File("plugins/HyDowned")
        try {
            config = DownedConfig.load(pluginDataFolder)

            // Initialize logger with configured log level
            com.hydowned.util.Log.setLogLevel(config.logLevel)

            Log.verbose("Plugin", "Configuration loaded")
            Log.verbose("Plugin", "  - Downed Timer: ${config.downedTimerSeconds}s")
            Log.verbose("Plugin", "  - Revive Timer: ${config.reviveTimerSeconds}s")
            Log.verbose("Plugin", "  - Downed Speed: ${config.downedSpeedMultiplier * 100}%")
            Log.verbose("Plugin", "  - Log Level: ${config.logLevel}")
        } catch (e: Exception) {
            Log.error("Plugin", "Failed to load configuration: ${e.message}")
            e.printStackTrace()
            throw e
        }

        // Register DownedComponent with ECS
        downedComponentType = entityStoreRegistry.registerComponent(
            DownedComponent::class.java
        ) { DownedComponent(0) }
        Log.verbose("Plugin", "DownedComponent registered")

        // Register PhantomBodyMarker component
        phantomBodyMarkerComponentType = entityStoreRegistry.registerComponent(
            PhantomBodyMarker::class.java
        ) { PhantomBodyMarker(null, null, null) }
        Log.verbose("Plugin", "PhantomBodyMarker component registered")

        // Register login cleanup system (runs sanity checks when player logs in)
        entityStoreRegistry.registerSystem(DownedLoginCleanupSystem(config))
        Log.verbose("Plugin", "Login cleanup system registered (sanity checks on login)")

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))
        Log.verbose("Plugin", "Death interception system registered")

        // ========== PHANTOM BODY APPROACH ==========
        // Phantom body system (testing if this causes skin issues)
        entityStoreRegistry.registerSystem(DownedPhantomBodySystem(config))
        Log.verbose("Plugin", "Phantom body system DISABLED (testing)")

        // Phantom body animation system (testing if this causes skin issues)
        entityStoreRegistry.registerSystem(PhantomBodyAnimationSystem(config))
        Log.verbose("Plugin", "Phantom body animation system")

        // Register player scale system (makes player tiny - SCALE mode only)
        entityStoreRegistry.registerSystem(DownedPlayerScaleSystem(config))
        Log.verbose("Plugin", "Player scale system registered (SCALE mode: 0.01% size + hides nameplate)")

        // Register invisibility system (pure invisible - INVISIBLE mode only)
        entityStoreRegistry.registerSystem(DownedInvisibilitySystem(config))
        Log.verbose("Plugin", "Invisibility system registered (INVISIBLE mode: HiddenFromAdventurePlayers + hides nameplate)")

        // Register collision disable system (disables player-to-player collision, keeps wall collision)
        entityStoreRegistry.registerSystem(DownedCollisionDisableSystem(config))
        Log.verbose("Plugin", "Collision disable system registered (character collisions disabled, block collisions active)")

        // Register radius constraint system (keeps player within 10 blocks of body)
        entityStoreRegistry.registerSystem(DownedRadiusConstraintSystem(config))
        Log.verbose("Plugin", "Radius constraint system registered (10 block radius)")

        // Register damage immunity system for downed players
        entityStoreRegistry.registerSystem(DownedDamageImmunitySystem(config))
        Log.verbose("Plugin", "Damage immunity system registered (downed players immune to damage)")

        // Register healing suppression system for downed players
        entityStoreRegistry.registerSystem(DownedHealingSuppressionSystem(config))
        Log.verbose("Plugin", "Healing suppression system registered (downed players cannot heal)")

        // Register clear effects system (clears buffs/debuffs when downed)
        entityStoreRegistry.registerSystem(DownedClearEffectsSystem(config))
        Log.verbose("Plugin", "Clear effects system registered (clears all active effects when downed)")

        // Register timer system for downed state countdown
        entityStoreRegistry.registerSystem(DownedTimerSystem(config))
        Log.verbose("Plugin", "Downed timer system registered")

        // Register revive interaction system (proximity-based)
        entityStoreRegistry.registerSystem(ReviveInteractionSystem(config))
        Log.verbose("Plugin", "Revive interaction system registered (proximity-based)")

        // Register packet interceptor system (PARTIALLY ACTIVE - interactions blocked, ClientMovement commented out)
        entityStoreRegistry.registerSystem(DownedPacketInterceptorSystem(config))
        Log.verbose("Plugin", "Packet interceptor system registered (interactions blocked, movement allowed)")

        // Movement suppression system - DISABLED (phantom body approach allows free movement)
        // entityStoreRegistry.registerSystem(DownedMovementSuppressionSystem(config))
        Log.error("Plugin", "Movement suppression system DISABLED (player can move freely within radius)")

        // Teleport lock system - DISABLED (replaced by radius constraint)
        // entityStoreRegistry.registerSystem(DownedTeleportLockSystem(config))
        Log.error("Plugin", "Teleport lock system DISABLED (replaced by radius constraint)")

        // Register interaction removal system (removes Interactions component completely)
        entityStoreRegistry.registerSystem(DownedRemoveInteractionsSystem(config))
        Log.verbose("Plugin", "Interaction removal system registered (removes Interactions + Interactable components)")

        // Register interaction blocking system (clears InteractionManager every tick)
        entityStoreRegistry.registerSystem(DownedInteractionBlockingSystem(config))
        Log.verbose("Plugin", "Interaction blocking system registered (clears InteractionManager to block all interactions)")

        // Register item disable system (removes items from hand to prevent combat crash)
        entityStoreRegistry.registerSystem(DownedDisableItemsSystem(config))
        Log.verbose("Plugin", "Item disable system registered (clears active hotbar slot to prevent combat)")

        // Register logout handler system (handles player disconnect while downed)
        entityStoreRegistry.registerSystem(DownedLogoutHandlerSystem(config))
        Log.verbose("Plugin", "Logout handler system registered (cleans up visibility + executes death on disconnect)")

        // Register commandsaa
        commandRegistry.registerCommand(GiveUpCommand(config))
        Log.verbose("Plugin", "/giveup command registered (allows downed players to immediately die)")

        // Register event listeners
        val playerReadyListener = PlayerReadyEventListener(config)
        eventRegistry.registerGlobal(PlayerReadyEvent::class.java, playerReadyListener::onPlayerReady)
        Log.verbose("Plugin", "PlayerReadyEvent listener registered (queues login cleanup)")

        /*val playerInteractListener = PlayerInteractListener(config)
        eventRegistry.registerGlobal(PlayerInteractEvent::class.java, playerInteractListener::onPlayerInteract)
        Log.verbose("Plugin", "PlayerInteractEvent listener registered (handles revive interactions)")*/

        Log.separator("Plugin")
    }

    override fun start() {
        Log.separator("Plugin")
        Log.verbose("Plugin", "Start Phase")
        Log.separator("Plugin")

        Log.separator("Plugin")
        Log.verbose("Plugin", "HyDowned plugin started successfully!")
        Log.verbose("Plugin", "Status: PHANTOM BODY APPROACH ACTIVE")
        Log.verbose("Plugin", "Invisibility Mode: ${config.invisibilityMode}")
        Log.separator("Plugin")
        Log.verbose("Plugin", "FEATURES:")
        Log.verbose("Plugin", "  ✓ Phantom body spawned at downed location (visible to all)")
        Log.verbose("Plugin", "  ✓ Death animation plays on phantom body")

        if (config.useScaleMode) {
            Log.verbose("Plugin", "  ✓ Downed player: SCALE mode (0.01% size, nameplate hidden)")
        } else if (config.useInvisibleMode) {
            Log.verbose("Plugin", "  ✓ Downed player: INVISIBLE mode (VisibilityComponent, nameplate hidden)")
        }

        Log.verbose("Plugin", "  ✓ Character collision disabled (no pushing/attacking players)")
        Log.verbose("Plugin", "  ✓ Block collision enabled (can't walk through walls)")
        Log.verbose("Plugin", "  ✓ 10 block movement radius from phantom body")
        Log.verbose("Plugin", "  ✓ Interactions blocked while downed")
        Log.verbose("Plugin", "  ✓ Immune to damage while downed")
        Log.verbose("Plugin", "  ✓ Healing blocked while downed")
        Log.verbose("Plugin", "  ✓ All active effects cleared when downed")
        Log.verbose("Plugin", "  ✓ Timer: ${config.downedTimerSeconds}s until death")
        Log.verbose("Plugin", "  ✓ Revive: CROUCH near phantom body (${config.reviveRange} blocks)")
        Log.verbose("Plugin", "  ✓ Player teleports back to body on revive")
        Log.verbose("Plugin", "  ✓ Logout while downed: auto-death + visibility restoration")
        Log.verbose("Plugin", "  ✓ Command: /giveup to instantly die while downed")
        Log.separator("Plugin")
    }

    override fun shutdown() {
        Log.verbose("Plugin", "Shutting down...")
        instance = null
        Log.verbose("Plugin", "Shutdown complete")
    }
}
