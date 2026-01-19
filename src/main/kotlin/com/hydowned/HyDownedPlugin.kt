package com.hydowned

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.state.DownedStateManager
import com.hydowned.systems.DownedDeathInterceptor
import com.hydowned.systems.DownedTimerSystem
import com.hydowned.systems.DownedVisualEffectsSystem
import java.util.concurrent.ScheduledFuture

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
    private lateinit var stateManager: DownedStateManager
    private lateinit var downedComponentType: ComponentType<EntityStore, DownedComponent>
    private var timerTask: ScheduledFuture<*>? = null

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

        // Initialize state manager
        stateManager = DownedStateManager(config)
        println("[HyDowned] ✓ State manager initialized")

        // Register DownedComponent with ECS
        downedComponentType = entityStoreRegistry.registerComponent(
            DownedComponent::class.java
        ) { DownedComponent(0) }
        println("[HyDowned] ✓ DownedComponent registered")

        // Register death interception system
        entityStoreRegistry.registerSystem(DownedDeathInterceptor(config))
        println("[HyDowned] ✓ Death interception system registered")

        // Register timer system for downed state countdown
        entityStoreRegistry.registerSystem(DownedTimerSystem(config))
        println("[HyDowned] ✓ Downed timer system registered")

        // Register visual effects system
        entityStoreRegistry.registerSystem(DownedVisualEffectsSystem(config))
        println("[HyDowned] ✓ Visual effects system registered")

        println("[HyDowned] ============================================")
    }

    override fun start() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Start Phase")
        println("[HyDowned] ============================================")

        // TODO: Register commands when command API is understood
        println("[HyDowned] ⚠ Commands not yet registered (API TBD)")

        // TODO: Register event listeners for revive interactions
        println("[HyDowned] ⚠ Revive interaction not yet implemented (API TBD)")

        println("[HyDowned] ============================================")
        println("[HyDowned] ✓ HyDowned plugin started successfully!")
        println("[HyDowned] Status: Death interception ACTIVE")
        println("[HyDowned] ✓ Players will enter downed state instead of dying")
        println("[HyDowned] ✓ Timer will execute death after ${config.downedTimerSeconds}s")
        println("[HyDowned] ============================================")
    }

    override fun shutdown() {
        println("[HyDowned] Shutting down...")

        // Cancel timer task if running
        timerTask?.cancel(false)
        timerTask = null

        // Execute death for any remaining downed players
        stateManager.cleanup()

        instance = null
        println("[HyDowned] Shutdown complete")
    }
}
