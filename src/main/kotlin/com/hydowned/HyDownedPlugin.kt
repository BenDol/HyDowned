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
        println("[HyDowned] ============================================")
        println("[HyDowned] Setup Phase")
        println("[HyDowned] ============================================")

        instance = this

        // Initialize configuration
        val pluginDataFolder = java.io.File("plugins/HyDowned")
        try {
            config = DownedConfig.load(pluginDataFolder)
            println("[HyDowned] ✓ Configuration loaded")
            println("[HyDowned]   - Downed Timer: ${config.downedTimerSeconds}s")
            println("[HyDowned]   - Revive Timer: ${config.reviveTimerSeconds}s")
            println("[HyDowned]   - Downed Speed: ${config.downedSpeedMultiplier * 100}%")
        } catch (e: Exception) {
            println("[HyDowned] ✗ Failed to load configuration: ${e.message}")
            e.printStackTrace()
            throw e
        }

        // Register DownedComponent with ECS
        downedComponentType = entityStoreRegistry.registerComponent(
            DownedComponent::class.java
        ) { DownedComponent(0) }
        println("[HyDowned] ✓ DownedComponent registered")

        // Register PhantomBodyMarker component
        phantomBodyMarkerComponentType = entityStoreRegistry.registerComponent(
            PhantomBodyMarker::class.java
        ) { PhantomBodyMarker(null, null, null) }
        println("[HyDowned] ✓ PhantomBodyMarker component registered")

        // Register login cleanup system (runs sanity checks when player logs in)
        entityStoreRegistry.registerSystem(DownedLoginCleanupSystem(config))
        println("[HyDowned] ✓ Login cleanup system registered (sanity checks on login)")

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))
        println("[HyDowned] ✓ Death interception system registered")

        // ========== PHANTOM BODY APPROACH ==========
        // Phantom body system (testing if this causes skin issues)
        entityStoreRegistry.registerSystem(DownedPhantomBodySystem(config))
        println("[HyDowned] ✓ Phantom body system DISABLED (testing)")

        // Phantom body animation system (testing if this causes skin issues)
        entityStoreRegistry.registerSystem(PhantomBodyAnimationSystem(config))
        println("[HyDowned] ✓ Phantom body animation system")

        // Register player scale system (makes player tiny - SCALE mode only)
        entityStoreRegistry.registerSystem(DownedPlayerScaleSystem(config))
        println("[HyDowned] ✓ Player scale system registered (SCALE mode: 0.01% size + hides nameplate)")

        // Register invisibility system (pure invisible - INVISIBLE mode only)
        entityStoreRegistry.registerSystem(DownedInvisibilitySystem(config))
        println("[HyDowned] ✓ Invisibility system registered (INVISIBLE mode: HiddenFromAdventurePlayers + hides nameplate)")

        // Register collision disable system (disables player-to-player collision, keeps wall collision)
        entityStoreRegistry.registerSystem(DownedCollisionDisableSystem(config))
        println("[HyDowned] ✓ Collision disable system registered (character collisions disabled, block collisions active)")

        // Register radius constraint system (keeps player within 10 blocks of body)
        entityStoreRegistry.registerSystem(DownedRadiusConstraintSystem(config))
        println("[HyDowned] ✓ Radius constraint system registered (10 block radius)")

        // Register damage immunity system for downed players
        entityStoreRegistry.registerSystem(DownedDamageImmunitySystem(config))
        println("[HyDowned] ✓ Damage immunity system registered (downed players immune to damage)")

        // Register healing suppression system for downed players
        entityStoreRegistry.registerSystem(DownedHealingSuppressionSystem(config))
        println("[HyDowned] ✓ Healing suppression system registered (downed players cannot heal)")

        // Register clear effects system (clears buffs/debuffs when downed)
        entityStoreRegistry.registerSystem(DownedClearEffectsSystem(config))
        println("[HyDowned] ✓ Clear effects system registered (clears all active effects when downed)")

        // Register timer system for downed state countdown
        entityStoreRegistry.registerSystem(DownedTimerSystem(config))
        println("[HyDowned] ✓ Downed timer system registered")

        // Register revive interaction system (proximity-based)
        entityStoreRegistry.registerSystem(ReviveInteractionSystem(config))
        println("[HyDowned] ✓ Revive interaction system registered (proximity-based)")

        // Register packet interceptor system (PARTIALLY ACTIVE - interactions blocked, ClientMovement commented out)
        entityStoreRegistry.registerSystem(DownedPacketInterceptorSystem(config))
        println("[HyDowned] ✓ Packet interceptor system registered (interactions blocked, movement allowed)")

        // Movement suppression system - DISABLED (phantom body approach allows free movement)
        // entityStoreRegistry.registerSystem(DownedMovementSuppressionSystem(config))
        println("[HyDowned] ✗ Movement suppression system DISABLED (player can move freely within radius)")

        // Teleport lock system - DISABLED (replaced by radius constraint)
        // entityStoreRegistry.registerSystem(DownedTeleportLockSystem(config))
        println("[HyDowned] ✗ Teleport lock system DISABLED (replaced by radius constraint)")

        // Register interaction removal system (removes Interactions component completely)
        entityStoreRegistry.registerSystem(DownedRemoveInteractionsSystem(config))
        println("[HyDowned] ✓ Interaction removal system registered (removes Interactions + Interactable components)")

        // Register interaction blocking system (clears InteractionManager every tick)
        entityStoreRegistry.registerSystem(DownedInteractionBlockingSystem(config))
        println("[HyDowned] ✓ Interaction blocking system registered (clears InteractionManager to block all interactions)")

        // Register item disable system (removes items from hand to prevent combat crash)
        entityStoreRegistry.registerSystem(DownedDisableItemsSystem(config))
        println("[HyDowned] ✓ Item disable system registered (clears active hotbar slot to prevent combat)")

        // Register logout handler system (handles player disconnect while downed)
        entityStoreRegistry.registerSystem(DownedLogoutHandlerSystem(config))
        println("[HyDowned] ✓ Logout handler system registered (cleans up visibility + executes death on disconnect)")

        // Register commandsaa
        commandRegistry.registerCommand(GiveUpCommand(config))
        println("[HyDowned] ✓ /giveup command registered (allows downed players to immediately die)")

        // Register event listeners
        val playerReadyListener = PlayerReadyEventListener(config)
        eventRegistry.registerGlobal(PlayerReadyEvent::class.java, playerReadyListener::onPlayerReady)
        println("[HyDowned] ✓ PlayerReadyEvent listener registered (queues login cleanup)")

        /*val playerInteractListener = PlayerInteractListener(config)
        eventRegistry.registerGlobal(PlayerInteractEvent::class.java, playerInteractListener::onPlayerInteract)
        println("[HyDowned] ✓ PlayerInteractEvent listener registered (handles revive interactions)")*/

        println("[HyDowned] ============================================")
    }

    override fun start() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Start Phase")
        println("[HyDowned] ============================================")

        println("[HyDowned] ============================================")
        println("[HyDowned] ✓ HyDowned plugin started successfully!")
        println("[HyDowned] Status: PHANTOM BODY APPROACH ACTIVE")
        println("[HyDowned] Invisibility Mode: ${config.invisibilityMode}")
        println("[HyDowned] ============================================")
        println("[HyDowned] FEATURES:")
        println("[HyDowned]   ✓ Phantom body spawned at downed location (visible to all)")
        println("[HyDowned]   ✓ Death animation plays on phantom body")

        if (config.useScaleMode) {
            println("[HyDowned]   ✓ Downed player: SCALE mode (0.01% size, nameplate hidden)")
        } else if (config.useInvisibleMode) {
            println("[HyDowned]   ✓ Downed player: INVISIBLE mode (VisibilityComponent, nameplate hidden)")
        }

        println("[HyDowned]   ✓ Character collision disabled (no pushing/attacking players)")
        println("[HyDowned]   ✓ Block collision enabled (can't walk through walls)")
        println("[HyDowned]   ✓ 10 block movement radius from phantom body")
        println("[HyDowned]   ✓ Interactions blocked while downed")
        println("[HyDowned]   ✓ Immune to damage while downed")
        println("[HyDowned]   ✓ Healing blocked while downed")
        println("[HyDowned]   ✓ All active effects cleared when downed")
        println("[HyDowned]   ✓ Timer: ${config.downedTimerSeconds}s until death")
        println("[HyDowned]   ✓ Revive: CROUCH near phantom body (${config.reviveRange} blocks)")
        println("[HyDowned]   ✓ Player teleports back to body on revive")
        println("[HyDowned]   ✓ Logout while downed: auto-death + visibility restoration")
        println("[HyDowned]   ✓ Command: /giveup to instantly die while downed")
        println("[HyDowned] ============================================")
    }

    override fun shutdown() {
        println("[HyDowned] Shutting down...")
        instance = null
        println("[HyDowned] Shutdown complete")
    }
}
