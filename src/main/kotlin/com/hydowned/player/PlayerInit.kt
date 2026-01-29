package com.hydowned.player

import com.hydowned.ModPlugin
import com.hydowned.player.system.CrouchDetectionSystem
import com.hydowned.player.system.DamageInterceptorSystem
import com.hydowned.player.system.PlayerRefSystem
import com.hydowned.player.system.PlayerTickSystem
import com.hydowned.logging.Log

class PlayerInit(plugin: ModPlugin) {

    init {
        PlayerEvents(plugin)

        // Register ECS systems
        plugin.entityStoreRegistry.registerSystem(PlayerRefSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(DamageInterceptorSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(PlayerTickSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(CrouchDetectionSystem(plugin.managers))

        Log.info("Plugin", "Player systems and events initialized")
    }
}