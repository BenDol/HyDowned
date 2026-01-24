package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.commands.GiveUpCommand
import com.hydowned.util.DownedCleanupHelper
import com.hydowned.util.Log

/**
 * System that ticks down the downed timer and executes death when it expires
 *
 * Runs every 1 second (DelayedEntitySystem with 1.0f delay)
 */
class DownedTimerSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(1.0f) {

    private val query = Query.and(
        DownedComponent.getComponentType(),
        Player.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val ref = archetypeChunk.getReferenceTo(index)
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return

        // Process any pending give-up commands for this entity
        val pendingGiveUp = GiveUpCommand.pendingGiveUps.remove(ref)
        if (pendingGiveUp != null && pendingGiveUp) {
            Log.finer("TimerSystem", "Processing giveup command")
            Log.finer("TimerSystem", "Timer before death: ${downedComponent.downedTimeRemaining}")

            // Execute death immediately
            DownedCleanupHelper.executeDeath(
                ref,
                commandBuffer,
                downedComponent,
                reason = "gave up"
            )

            Log.finer("TimerSystem", "Giveup death executed")
            return // Exit early - player gave up
        }

        // Get player component for sending messages
        val playerComponent = archetypeChunk.getComponent(index, Player.getComponentType())

        // Decrement timer (dt is in seconds, approximately 1.0)
        downedComponent.downedTimeRemaining -= 1

        val timeRemaining = downedComponent.downedTimeRemaining

        // PERFORMANCE: Commented out (runs every 1s for every downed player)
        // Log.debug("TimerSystem", "Timer tick: ${timeRemaining}s remaining")

        // Send chat messages at specific intervals (only if not being revived)
        if (playerComponent != null && downedComponent.reviverPlayerIds.isEmpty()) {
            when (timeRemaining) {
                60, 30, 10 -> playerComponent.sendMessage(
                    Message.translation("hydowned.timer.remaining_short")
                        .param("time", timeRemaining)
                )
                else -> {
                    // Every 30 seconds for longer timers
                    if (timeRemaining > 60 && timeRemaining % 30 == 0) {
                        playerComponent.sendMessage(
                            Message.translation("hydowned.timer.knocked_out_remaining")
                                .param("time", timeRemaining)
                        )
                    }
                }
            }
        }

        // Check if revivers are still present
        if (downedComponent.reviverPlayerIds.isNotEmpty()) {
            // Calculate revive speed based on reviver count
            val reviverCount = downedComponent.reviverPlayerIds.size
            val speedMultiplier = if (config.multipleReviversMode == "SPEEDUP") {
                1.0 + ((reviverCount - 1) * config.reviveSpeedupPerPlayer)
            } else {
                1.0
            }

            // Decrement revive timer
            val oldReviveTime = downedComponent.reviveTimeRemaining
            downedComponent.reviveTimeRemaining -= speedMultiplier

            // PERFORMANCE: Commented out (runs every 1s for every downed player being revived)
            // Log.debug("TimerSystem", "Revivers: ${reviverCount}, Speed: ${speedMultiplier}x, Remaining: ${downedComponent.reviveTimeRemaining}s")

            // Send countdown messages (only on whole second changes)
            val oldSeconds = oldReviveTime.toInt()
            val newSeconds = downedComponent.reviveTimeRemaining.toInt()

            if (oldSeconds != newSeconds && newSeconds >= 0) {
                // Get all players from Universe to find revivers
                val allPlayers = com.hypixel.hytale.server.core.universe.Universe.get().players
                val reviverNames = mutableListOf<String>()

                // Find reviver names
                for (player in allPlayers) {
                    if (downedComponent.reviverPlayerIds.contains(player.uuid.toString())) {
                        reviverNames.add(player.username)

                        // Send countdown to reviver every second
                        if (newSeconds <= 10 || newSeconds % 2 == 0) {
                            player.sendMessage(
                                Message.translation("hydowned.timer.reviving_countdown")
                                    .param("time", newSeconds)
                            )
                        }
                    }
                }

                // Send countdown to downed player
                if (playerComponent != null) {
                    if (newSeconds <= 10 || newSeconds % 2 == 0) {
                        if (reviverNames.size == 1) {
                            playerComponent.sendMessage(
                                Message.translation("hydowned.timer.player_reviving_single")
                                    .param("reviverName", reviverNames[0])
                                    .param("time", newSeconds)
                            )
                        } else {
                            playerComponent.sendMessage(
                                Message.translation("hydowned.timer.player_reviving_multiple")
                                    .param("count", reviverNames.size)
                                    .param("time", newSeconds)
                            )
                        }
                    }
                }
            }

            // Check if revive complete
            if (downedComponent.reviveTimeRemaining <= 0) {
                Log.separator("TimerSystem")
                Log.info("TimerSystem", "Revive complete!")
                Log.separator("TimerSystem")

                // Use centralized cleanup helper to handle revive
                val reviveSuccess = DownedCleanupHelper.executeRevive(
                    ref,
                    commandBuffer,
                    downedComponent,
                    config.reviveHealthPercent
                )

                if (reviveSuccess) {
                    // Send success message to downed player
                    playerComponent?.sendMessage(Message.translation("hydowned.timer.revived"))

                    // Notify revivers of success
                    val allPlayers = com.hypixel.hytale.server.core.universe.Universe.get().players
                    for (player in allPlayers) {
                        if (downedComponent.reviverPlayerIds.contains(player.uuid.toString())) {
                            val downedPlayerName = playerComponent?.displayName ?: "Player"
                            player.sendMessage(
                                Message.translation("hydowned.timer.revived_player")
                                    .param("playerName", downedPlayerName)
                            )
                        }
                    }

                    Log.info("TimerSystem", "Player revived successfully")
                } else {
                    Log.warning("TimerSystem", "Revive completed but health restoration failed")
                }

                return // Exit early - player is revived
            }
        }

        // Check if timer expired
        if (timeRemaining <= 0) {
            // Use centralized cleanup helper to handle death
            DownedCleanupHelper.executeDeath(
                ref,
                commandBuffer,
                downedComponent,
                "Timer expired"
            )

            Log.finer("TimerSystem", "Death executed, normal respawn flow will proceed")
        }
    }

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
        // Run in parallel for better performance
        return true
    }
}
