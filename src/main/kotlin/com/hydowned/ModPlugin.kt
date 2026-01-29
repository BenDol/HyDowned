package com.hydowned

import com.hydowned.command.GiveUpCommand
import com.hydowned.component.DownedComponent
import com.hydowned.config.ModConfig
import com.hydowned.network.DownedPlayerPacketFilter
import com.hydowned.manager.Managers
import com.hydowned.player.PlayerInit
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.logging.Log
import java.util.concurrent.Executors

class ModPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        var instance: ModPlugin? = null
    }

    lateinit var config: ModConfig
    lateinit var managers: Managers
    private lateinit var counterComponentType: ComponentType<EntityStore, DownedComponent>

    override fun setup() {
        instance = this

        // Load configuration
        val pluginDataFolder = java.io.File("plugins/HyDowned")
        try {
            config = ModConfig.load(pluginDataFolder)
        } catch (e: Exception) {
            config = ModConfig()
            e.printStackTrace()
        }

        // Initialize logging
        Log.init(logger, config.logLevel)
        Log.info("Plugin", "Configuration loaded")

        // Initialize managers
        managers = Managers(config)
        Log.info("Plugin", "Managers initialized")

        // Initialize systems
        init()

        // Register commands
        commandRegistry.registerCommand(GiveUpCommand(config))
        Log.info("Plugin", "Commands registered")

        // Start tick timer (runs every tick = 1/20th second)
        startTicks()

        // Register component
        counterComponentType = entityStoreRegistry.registerComponent(
            DownedComponent::class.java,
            "DownedComponent",
            DownedComponent.CODEC
        )
        DownedComponent.setComponentType(counterComponentType)
        Log.info("Plugin", "Components registered")

        // Register packet listener
        PacketAdapters.registerInbound(DownedPlayerPacketFilter())
        Log.info("Plugin", "Packet filter registered")

        Log.info("Plugin", "HyDowned setup complete")
    }

    private fun init() {
        PlayerInit(this)
    }

    private fun startTicks() {
        // Schedule repeating task every tick (1/20th second)
        val task = Runnable {
            managers.tick()
        }

        // Run async repeating task
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            task,
            0L,
            50L, // 50ms = 1 tick
            java.util.concurrent.TimeUnit.MILLISECONDS
        )

        Log.info("Plugin", "Tick timer started")
    }

    override fun start() {
        Log.info("Plugin", "HyDowned started successfully")
    }

    override fun shutdown() {
        instance = null
        Log.info("Plugin", "Shutdown complete")
    }
}

