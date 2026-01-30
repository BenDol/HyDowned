package com.hydowned.aspect

import com.hydowned.manager.PlayerManager
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.server.core.entity.EntityUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

interface Downable : Aspect {
    fun isDowned(): Boolean
    fun isDead(): Boolean
    fun isAlive(): Boolean
    fun getDownProgress(): Float
    fun getDownDuration(): Double
    fun getTimeRemaining(): Double
    fun getAggressor(): Aspect?
    fun getCurrentReviver(): Reviver?
    fun onDown(aggressor: Aspect)
    fun onDeath()
    fun onRevived()
    fun onCancelDown()
    fun canBeRevivedBy(reviver: Reviver): Boolean
    fun canDie(): Boolean
    fun tick()

    companion object {
        fun find(index: Int, archetypeChunk: ArchetypeChunk<EntityStore>, playerManager: PlayerManager): Downable? {
            val holder = EntityUtils.toHolder(index, archetypeChunk)
            val player = holder.getComponent(Player.getComponentType()) ?: return null
            val playerRef = holder.getComponent(PlayerRef.getComponentType()) ?: return null
            val revivePlayer = playerManager.get(player) ?: return null
            return revivePlayer.asDownable
        }
    }
}
