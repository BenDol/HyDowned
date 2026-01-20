package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.FormattedMessage
import com.hypixel.hytale.protocol.MaybeBool
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Displays event title HUD for downed players showing:
 * 1. Death timer countdown (primary title)
 * 2. Revive progress (secondary subtitle when being revived)
 *
 * Updates every 0.5 seconds for smooth updates without spam
 */
class DownedHudSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(0.5f) {

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        PlayerRef.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return
        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return

        // Calculate progress percentages
        val deathProgress = downedComponent.downedTimeRemaining.toFloat() / config.downedTimerSeconds.toFloat()
        val deathPercent = (deathProgress * 100).toInt().coerceIn(0, 100)

        // Format time remaining
        val timeRemaining = downedComponent.downedTimeRemaining
        val minutes = timeRemaining / 60
        val seconds = timeRemaining % 60
        val timeStr = if (minutes > 0) {
            String.format("%d:%02d", minutes, seconds)
        } else {
            String.format("%ds", seconds)
        }

        // Create progress bar
        val barLength = 20
        val filledBars = (deathProgress * barLength).toInt().coerceIn(0, barLength)
        val emptyBars = barLength - filledBars
        val progressBar = "=".repeat(filledBars) + "-".repeat(emptyBars)

        // Primary title: Death timer with progress bar (NO COLOR CODES)
        val primaryText = "DOWNED: $timeStr [$progressBar] $deathPercent%"

        // Secondary subtitle: Revive progress if being revived (NO COLOR CODES)
        val secondaryText = if (downedComponent.reviverPlayerIds.isNotEmpty()) {
            val reviveProgress = 1.0f - (downedComponent.reviveTimeRemaining.toFloat() / config.reviveTimerSeconds.toFloat())
            val revivePercent = (reviveProgress * 100).toInt().coerceIn(0, 100)
            val reviveFilledBars = (reviveProgress * barLength).toInt().coerceIn(0, barLength)
            val reviveEmptyBars = barLength - reviveFilledBars
            val reviveProgressBar = "=".repeat(reviveFilledBars) + "-".repeat(reviveEmptyBars)

            val reviverCount = downedComponent.reviverPlayerIds.size
            val reviverText = if (reviverCount > 1) " ($reviverCount helpers)" else ""
            "REVIVING: [$reviveProgressBar] $revivePercent%$reviverText"
        } else {
            null
        }

        // Send ShowEventTitle packet
        sendEventTitle(playerRefComponent, primaryText, secondaryText)
    }

    private fun sendEventTitle(playerRef: PlayerRef, primaryText: String, secondaryText: String?) {
        try {
            val titlePacket = ShowEventTitle()

            // Set ALL required fields explicitly
            titlePacket.fadeInDuration = 0.0f
            titlePacket.fadeOutDuration = 0.0f
            titlePacket.duration = 1.0f
            titlePacket.isMajor = false
            titlePacket.icon = null

            // MINIMAL TEST: Send empty primary title
            val primaryMessage = FormattedMessage()
            primaryMessage.rawText = ""  // EMPTY STRING TO TEST
            primaryMessage.markupEnabled = false
            primaryMessage.bold = MaybeBool.Null
            primaryMessage.italic = MaybeBool.Null
            primaryMessage.monospace = MaybeBool.Null
            primaryMessage.underlined = MaybeBool.Null
            titlePacket.primaryTitle = primaryMessage

            // No secondary title for testing
            titlePacket.secondaryTitle = null

            // Send packet
            playerRef.packetHandler.write(titlePacket)
        } catch (e: Exception) {
            Log.verbose("HudSystem", "Error sending event title HUD: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
        // Run in parallel for better performance
        return true
    }
}
