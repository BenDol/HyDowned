package com.hydowned.player

import com.hydowned.ModPlugin
import com.hydowned.player.system.*
import com.hydowned.logging.Log

class PlayerInit(plugin: ModPlugin) {

    init {
        PlayerEvents(plugin)

        // Register ECS systems
        plugin.entityStoreRegistry.registerSystem(PlayerRefSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(ApplyDamageSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(PlayerTickSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(CrouchDetectionSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(OnDeathSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(BlockBreakSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(BlockPlaceSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(SprintStaminaSystem(plugin.managers))
        plugin.entityStoreRegistry.registerSystem(AITargetCleanSystem(plugin.managers))

        Log.info("Plugin", "Player systems and events initialized")
    }
}