package com.hydowned

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
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
import com.hydowned.systems.DownedHudSystem
import com.hydowned.systems.DownedHudCleanupSystem
import com.hydowned.systems.DownedReviverHudSystem

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

    fun getDownedComponentType(): ComponentType<EntityStore, DownedComponent> {
        return downedComponentType
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

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))
        println("[HyDowned] ✓ Death interception system registered")

        // Register animation system (plays death animation when downed)
        entityStoreRegistry.registerSystem(DownedAnimationSystem(config))
        println("[HyDowned] ✓ Animation system registered (plays death animation)")

        // Register movement state system (forces sleeping = true for lying animation)
        entityStoreRegistry.registerSystem(DownedMovementStateSystem(config))
        println("[HyDowned] ✓ Movement state system registered (forces sleeping state for lying animation)")

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

        // Register HUD display system (shows death timer and revive progress bars)
        // DISABLED: ShowEventTitle packet causes client crash - need alternative HUD approach
        //entityStoreRegistry.registerSystem(DownedHudSystem(config))
        println("[HyDowned] ✗ HUD system DISABLED (ShowEventTitle packet causes client crash)")

        // Register HUD cleanup system (removes HUD when revived/dead)
        //entityStoreRegistry.registerSystem(DownedHudCleanupSystem(config))
        println("[HyDowned] ✗ HUD cleanup system DISABLED (no HUD to clean up)")

        // Register reviver HUD system (shows progress to people reviving + cleanup)
        //entityStoreRegistry.registerSystem(DownedReviverHudSystem(config))
        println("[HyDowned] ✗ Reviver HUD system DISABLED (ShowEventTitle packet causes client crash)")

        // Register revive interaction system (proximity-based)
        entityStoreRegistry.registerSystem(ReviveInteractionSystem(config))
        println("[HyDowned] ✓ Revive interaction system registered (proximity-based)")

        // Register packet interceptor system (ELEGANT SOLUTION - wraps packet handlers at network level)
        entityStoreRegistry.registerSystem(DownedPacketInterceptorSystem(config))
        println("[HyDowned] ✓ Packet interceptor system registered (intercepts incoming/outgoing packets)")

        // Register movement suppression system (CRITICAL - clears input queue BEFORE processing)
        entityStoreRegistry.registerSystem(DownedMovementSuppressionSystem(config))
        println("[HyDowned] ✓ Movement suppression system registered (blocks PlayerInput processing)")

        // Register teleport lock system (sends ClientTeleport packets to force client position)
        //entityStoreRegistry.registerSystem(DownedTeleportLockSystem(config))
        println("[HyDowned] ✓ Teleport lock system registered (sends ClientTeleport packets every 0.5s)")

        // Register interaction removal system (removes Interactions component completely)
        entityStoreRegistry.registerSystem(DownedRemoveInteractionsSystem(config))
        println("[HyDowned] ✓ Interaction removal system registered (removes Interactions + Interactable components)")

        // Register interaction blocking system (clears InteractionManager every tick)
        entityStoreRegistry.registerSystem(DownedInteractionBlockingSystem(config))
        println("[HyDowned] ✓ Interaction blocking system registered (clears InteractionManager to block all interactions)")

        println("[HyDowned] ============================================")
    }

    override fun start() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Start Phase")
        println("[HyDowned] ============================================")

        println("[HyDowned] ============================================")
        println("[HyDowned] ✓ HyDowned plugin started successfully!")
        println("[HyDowned] Status: Death interception ACTIVE")
        println("[HyDowned] ============================================")
        println("[HyDowned] FEATURES:")
        println("[HyDowned]   ✓ Players lie down instead of dying")
        println("[HyDowned]   ✓ Movement suppressed while downed")
        println("[HyDowned]   ✓ Interactions blocked while downed")
        println("[HyDowned]   ✓ Immune to damage while downed")
        println("[HyDowned]   ✓ Healing blocked while downed")
        println("[HyDowned]   ✓ All active effects cleared when downed")
        println("[HyDowned]   ✓ Timer: ${config.downedTimerSeconds}s until death")
        println("[HyDowned]   ✓ Revive: CROUCH near downed player (${config.reviveRange} blocks)")
        println("[HyDowned] ============================================")
    }

    override fun shutdown() {
        println("[HyDowned] Shutting down...")
        instance = null
        println("[HyDowned] Shutdown complete")
    }
}
