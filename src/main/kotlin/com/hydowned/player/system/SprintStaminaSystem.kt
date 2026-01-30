package com.hydowned.player.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.entity.EntityUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.stamina.StaminaSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.manager.Managers

/**
 * Drains stamina for downed players to prevent sprinting/abilities.
 *
 * Sets stamina to a very low negative value to prevent any stamina-based actions.
 */
class SprintStaminaSystem(private val managers: Managers) : StaminaSystems.SprintStaminaEffectSystem() {

    private val entityStatMapType: ComponentType<EntityStore, EntityStatMap> = EntityStatMap.getComponentType()

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val holder = EntityUtils.toHolder(index, archetypeChunk)
        val player = holder.getComponent(Player.getComponentType()) ?: return

        val revivePlayer = managers.playerManager.get(player) ?: return

        // If player is downed, drain their stamina completely
        if (revivePlayer.asDownable.isDowned()) {
            val statMap = archetypeChunk.getComponent(index, entityStatMapType) ?: return

            // Set stamina to very low negative value to prevent any stamina actions
            statMap.setStatValue(DefaultEntityStatTypes.getStamina(), -2.1474836E9f)
        }
    }
}
