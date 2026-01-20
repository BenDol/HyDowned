package com.hydowned

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.components.PhantomBodyMarker
import com.hydowned.config.DownedConfig
import com.hydowned.systems.DownedDeathInterceptor
import com.hydowned.systems.DownedTimerSystem
import com.hydowned.systems.ReviveInteractionSystem
import com.hydowned.systems.DownedMovementSuppressionSystem
import com.hydowned.systems.DownedTeleportLockSystem
import com.hydowned.systems.DownedDamageImmunitySystem
import com.hydowned.systems.DownedRemoveInteractionsSystem
import com.hydowned.systems.DownedHealingSuppressionSystem
import com.hydowned.systems.DownedClearEffectsSystem
import com.hydowned.systems.DownedPacketInterceptorSystem
import com.hydowned.systems.DownedAnimationSystem
import com.hydowned.systems.DownedInteractionBlockingSystem
import com.hydowned.systems.DownedMovementStateSystem
import com.hydowned.systems.DownedPhantomBodySystem
import com.hydowned.systems.DownedRadiusConstraintSystem
import com.hydowned.systems.PhantomBodyAnimationSystem
import com.hydowned.systems.DownedPlayerVisibilitySystem
import com.hydowned.systems.DownedLogoutHandlerSystem
import com.hydowned.systems.DownedDisableItemsSystem

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

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))
        println("[HyDowned] ✓ Death interception system registered")

        // ========== PHANTOM BODY APPROACH ==========
        // Register phantom body system (spawns NPC body at downed location)
        entityStoreRegistry.registerSystem(DownedPhantomBodySystem(config))
        println("[HyDowned] ✓ Phantom body system registered (spawns NPC at downed location)")

        // Register phantom body animation system (plays death animation after spawn)
        entityStoreRegistry.registerSystem(PhantomBodyAnimationSystem(config))
        println("[HyDowned] ✓ Phantom body animation system registered")

        // Register player visibility system (hides downed player, shows phantom body)
        entityStoreRegistry.registerSystem(DownedPlayerVisibilitySystem(config))
        println("[HyDowned] ✓ Player visibility system registered (hides downed player)")

        // Register radius constraint system (keeps player within 10 blocks of body)
        entityStoreRegistry.registerSystem(DownedRadiusConstraintSystem(config))
        println("[HyDowned] ✓ Radius constraint system registered (10 block radius)")

        // OLD: Animation system - DISABLED (phantom body shows animation instead)
        // entityStoreRegistry.registerSystem(DownedAnimationSystem(config))
        println("[HyDowned] ✗ Animation system DISABLED (phantom body shows animation)")

        // OLD: Movement state system - DISABLED (player can move freely)
        // entityStoreRegistry.registerSystem(DownedMovementStateSystem(config))
        println("[HyDowned] ✗ Movement state system DISABLED (phantom body approach)")

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

        println("[HyDowned] ============================================")
    }

    override fun start() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Start Phase")
        println("[HyDowned] ============================================")

        println("[HyDowned] ============================================")
        println("[HyDowned] ✓ HyDowned plugin started successfully!")
        println("[HyDowned] Status: PHANTOM BODY APPROACH ACTIVE")
        println("[HyDowned] ============================================")
        println("[HyDowned] FEATURES:")
        println("[HyDowned]   ✓ Phantom body spawned at downed location (visible to all)")
        println("[HyDowned]   ✓ Death animation plays on phantom body")
        println("[HyDowned]   ✓ Downed player is invisible (can move freely)")
        println("[HyDowned]   ✓ 10 block movement radius from phantom body")
        println("[HyDowned]   ✓ Interactions blocked while downed")
        println("[HyDowned]   ✓ Immune to damage while downed")
        println("[HyDowned]   ✓ Healing blocked while downed")
        println("[HyDowned]   ✓ All active effects cleared when downed")
        println("[HyDowned]   ✓ Timer: ${config.downedTimerSeconds}s until death")
        println("[HyDowned]   ✓ Revive: CROUCH near phantom body (${config.reviveRange} blocks)")
        println("[HyDowned]   ✓ Player teleports back to body on revive")
        println("[HyDowned]   ✓ Logout while downed: auto-death + visibility restoration")
        println("[HyDowned] ============================================")
    }

    override fun shutdown() {
        println("[HyDowned] Shutting down...")
        instance = null
        println("[HyDowned] Shutdown complete")
    }
}
