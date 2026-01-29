package com.hydowned.manager

import com.hydowned.config.ModConfig

/**
 * Container for all manager instances.
 * Provides centralized tick() method for all managers.
 */
class Managers(val config: ModConfig) {
    val downManager = DownManager(config)
    val reviveManager = ReviveManager(config)
    val playerManager = PlayerManager(config)
    val hudManager = HudManager(config)

    fun tick() {
        downManager.tick()
        reviveManager.tick()
    }
}
