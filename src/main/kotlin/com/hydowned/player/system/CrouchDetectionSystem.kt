package com.hydowned.player.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.EntityUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.extension.getDistance
import com.hydowned.extension.getNearestDownable
import com.hydowned.logging.Log
import com.hydowned.manager.Managers

/**
 * Crouching detector system.
 *
 * Detects when players start/stop crouching near downed players to start/cancel revives.
 */
class CrouchDetectionSystem(val managers: Managers) : EntityTickingSystem<EntityStore>() {
    private val query: Query<EntityStore> = Query.and(
        Player.getComponentType(),
        MovementStatesComponent.getComponentType()
    )

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val component = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType())
        val current = component?.movementStates ?: return
        val previous = component.sentMovementStates

        val holder = EntityUtils.toHolder(index, archetypeChunk)
        val player = holder.getComponent(Player.getComponentType()) ?: return
        val playerRef = holder.getComponent(PlayerRef.getComponentType()) ?: return

        val modPlayer = managers.playerManager.get(player) ?: return
        val downable = modPlayer.asDownable
        val reviver = modPlayer.asReviver

        // Detect crouching state changes
        if (current.crouching != previous.crouching) {
            if (current.crouching) {
                // Started crouching
                if (downable.isDowned()) {
                    // Downed player - start giveup countdown
                    downable.giveUpTicks = downable.getMaxGiveUpTicks()
                    Log.finer("CrouchDetection",
                        "${player.displayName} started giving up (${downable.giveUpTicks} ticks)")
                } else {
                    // Not downed - try to revive nearby player
                    val downableNearest = playerRef.getNearestDownable()
                    if (downableNearest != null && playerRef.getDistance(downableNearest.playerRef) <= 5.0) {
                        managers.reviveManager.start(reviver, downableNearest)
                        Log.info("CrouchDetection",
                            "${player.displayName} started reviving ${downableNearest.getDisplayName()}")
                    }
                }
            } else {
                // Stopped crouching
                if (downable.isDowned()) {
                    // Cancel giveup
                    downable.giveUpTicks = -1
                    Log.finer("CrouchDetection", "${player.displayName} canceled giveup")
                }
                if (managers.reviveManager.isReviving(reviver)) {
                    managers.reviveManager.cancel(reviver)
                    Log.info("CrouchDetection", "${player.displayName} canceled revive")
                }
            }
        }
    }

    override fun getQuery(): Query<EntityStore>? {
        return query
    }
}
