package com.hydowned.util

import com.hypixel.hytale.protocol.FormattedMessage
import com.hypixel.hytale.protocol.MaybeBool
import com.hypixel.hytale.protocol.packets.interface_.HideEventTitle
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle
import com.hypixel.hytale.server.core.universe.PlayerRef

/**
 * Utility for creating and sending HUD event title packets.
 * Reduces code duplication for HUD display across systems.
 */
object HudPacketBuilder {

    /**
     * Creates a FormattedMessage with safe defaults.
     *
     * @param text The text to display
     * @return FormattedMessage configured with safe defaults
     */
    fun createFormattedMessage(text: String): FormattedMessage {
        val message = FormattedMessage()
        message.rawText = text
        message.markupEnabled = false
        // Initialize @Nonnull MaybeBool fields to prevent client crashes
        message.bold = MaybeBool.Null
        message.italic = MaybeBool.Null
        message.monospace = MaybeBool.Null
        message.underlined = MaybeBool.Null
        return message
    }

    /**
     * Creates a ShowEventTitle packet with standard configuration.
     *
     * @param primaryText Primary title text (main message)
     * @param secondaryText Optional secondary subtitle text
     * @param duration How long the title should display (in seconds)
     * @param isMajor Whether this is a major title (interrupts gameplay)
     * @return Configured ShowEventTitle packet
     */
    fun createEventTitle(
        primaryText: String,
        secondaryText: String? = null,
        duration: Float = 1.0f,
        isMajor: Boolean = false
    ): ShowEventTitle {
        val titlePacket = ShowEventTitle()

        // Set durations
        titlePacket.fadeInDuration = 0.0f
        titlePacket.fadeOutDuration = 0.0f
        titlePacket.duration = duration

        // Set whether this is a major title
        titlePacket.isMajor = isMajor

        // No icon
        titlePacket.icon = null

        // Set primary title
        titlePacket.primaryTitle = createFormattedMessage(primaryText)

        // Set secondary title if provided
        titlePacket.secondaryTitle = if (secondaryText != null) {
            createFormattedMessage(secondaryText)
        } else {
            null
        }

        return titlePacket
    }

    /**
     * Sends an event title HUD to a player.
     *
     * @param playerRef Player to send HUD to
     * @param primaryText Primary title text
     * @param secondaryText Optional secondary subtitle text
     * @param duration How long the title should display (in seconds)
     * @param isMajor Whether this is a major title (interrupts gameplay)
     * @param systemName Name of calling system (for error logging)
     * @return true if packet was sent successfully, false otherwise
     */
    fun sendEventTitle(
        playerRef: PlayerRef,
        primaryText: String,
        secondaryText: String? = null,
        duration: Float = 1.0f,
        isMajor: Boolean = false,
        systemName: String
    ): Boolean {
        return try {
            val titlePacket = createEventTitle(primaryText, secondaryText, duration, isMajor)
            playerRef.packetHandler.write(titlePacket)
            true
        } catch (e: Exception) {
            Log.error(systemName, "Error sending event title HUD: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Hides the event title HUD from a player.
     *
     * @param playerRef Player to hide HUD from
     * @param systemName Name of calling system (for error logging)
     * @return true if packet was sent successfully, false otherwise
     */
    fun hideEventTitle(
        playerRef: PlayerRef,
        systemName: String
    ): Boolean {
        return try {
            val hidePacket = HideEventTitle()
            playerRef.packetHandler.write(hidePacket)
            true
        } catch (e: Exception) {
            Log.error(systemName, "Error hiding event title HUD: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
