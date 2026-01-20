package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.FormattedMessage
import com.hypixel.hytale.protocol.MaybeBool
import com.hypixel.hytale.protocol.packets.interface_.HideEventTitle
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import java.util.concurrent.ConcurrentHashMap
import com.hydowned.util.Log


/**
 * Displays HUD to players who are actively reviving a downed player.
 * Shows the revive progress bar to the reviver(s).
 *
 * This system queries ALL PLAYERS and checks if each player is reviving anyone.
 * If they are, it sends them HUD showing the revive progress.
 *
 * Updates every 0.5 seconds for smooth progress bar animation
 */
class DownedReviverHudSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(0.5f) {

    // Track which players currently have reviver HUD showing (thread-safe for parallel execution)
    private val playersWithHud = ConcurrentHashMap.newKeySet<String>()

    // Query all players (not just downed ones)
    private val query = Query.and(
        Player.getComponentType(),
        PlayerRef.getComponentType(),
        UUIDComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return
        val uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType())
            ?: return

        val playerId = uuidComponent.uuid.toString()

        // Check if this player is reviving anyone
        val downedPlayerInfo = findDownedPlayerBeingRevivedBy(playerId, archetypeChunk, store)

        if (downedPlayerInfo != null) {
            // Player is reviving someone - send HUD and track
            val (downedPlayer, downedComponent) = downedPlayerInfo
            val hudText = buildReviverHudText(downedPlayer, downedComponent)
            sendReviverHud(playerRef, hudText)
            playersWithHud.add(playerId)
        } else if (playersWithHud.contains(playerId)) {
            // Player WAS reviving but stopped - clear HUD
            hideReviverHud(playerRef)
            playersWithHud.remove(playerId)
        }
    }

    /**
     * Find the downed player that this player is reviving
     * Returns (downedPlayer, downedComponent) or null if not reviving anyone
     */
    private fun findDownedPlayerBeingRevivedBy(
        playerId: String,
        currentChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>
    ): Pair<Player, DownedComponent>? {
        // First check the current chunk (optimization)
        for (i in 0 until currentChunk.size()) {
            val downedComponent = currentChunk.getComponent(i, DownedComponent.getComponentType())
            val downedPlayer = currentChunk.getComponent(i, Player.getComponentType())

            if (downedComponent != null && downedPlayer != null) {
                if (downedComponent.reviverPlayerIds.contains(playerId)) {
                    return Pair(downedPlayer, downedComponent)
                }
            }
        }

        // If not found in current chunk, we can't easily search other chunks
        // The reviver HUD will just not show until the next tick when we might get the right chunk
        return null
    }

    /**
     * Build the HUD text for a reviver
     */
    private fun buildReviverHudText(downedPlayer: Player, downedComponent: DownedComponent): String {
        // Calculate revive progress
        val reviveProgress = 1.0f - (downedComponent.reviveTimeRemaining.toFloat() / config.reviveTimerSeconds.toFloat())
        val revivePercent = (reviveProgress * 100).toInt().coerceIn(0, 100)

        // Create progress bar
        val barLength = 20
        val filledBars = (reviveProgress * barLength).toInt().coerceIn(0, barLength)
        val emptyBars = barLength - filledBars
        val progressBar = "=".repeat(filledBars) + "-".repeat(emptyBars)

        // Format remaining time
        val timeStr = String.format("%.1fs", downedComponent.reviveTimeRemaining)

        // HUD text for revivers (NO COLOR CODES)
        val reviverCount = downedComponent.reviverPlayerIds.size
        val teamText = if (reviverCount > 1) " (${reviverCount} helpers)" else ""
        val playerName = downedPlayer.displayName ?: "Player"
        return "REVIVING: ${playerName} [$progressBar] $revivePercent% ($timeStr remaining)$teamText"
    }

    private fun sendReviverHud(playerRef: PlayerRef, hudText: String) {
        try {
            val titlePacket = ShowEventTitle()

            // Set durations
            titlePacket.fadeInDuration = 0.0f  // No fade in
            titlePacket.fadeOutDuration = 0.0f // No fade out
            titlePacket.duration = 1.0f        // Display for 1 second (will be updated before expiring)

            // Not a major title (don't interrupt gameplay)
            titlePacket.isMajor = false

            // No icon
            titlePacket.icon = null

            // Set title text
            val message = FormattedMessage()
            message.rawText = hudText
            message.markupEnabled = false  // No markup - plain text only
            // Initialize @Nonnull MaybeBool fields to prevent client crashes
            message.bold = MaybeBool.Null
            message.italic = MaybeBool.Null
            message.monospace = MaybeBool.Null
            message.underlined = MaybeBool.Null
            titlePacket.primaryTitle = message
            titlePacket.secondaryTitle = null

            // Send packet
            playerRef.packetHandler.write(titlePacket)
        } catch (e: Exception) {
            Log.error("ReviverHud", "Error sending reviver HUD: ${e.message}")
        }
    }

    private fun hideReviverHud(playerRef: PlayerRef) {
        try {
            val hidePacket = HideEventTitle()
            playerRef.packetHandler.write(hidePacket)
        } catch (e: Exception) {
            Log.error("ReviverHud", "Error hiding reviver HUD: ${e.message}")
        }
    }

    override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
        // Run in parallel for better performance
        return true
    }
}
