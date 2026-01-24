package com.hydowned.util

import com.hypixel.hytale.protocol.*
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

    /**
     * Creates a FormattedMessage using a translation key with parameters.
     *
     * @param translationKey The i18n key
     * @param params Map of parameter key-value pairs
     * @return FormattedMessage configured for translation
     */
    fun createTranslatedMessage(
        translationKey: String,
        params: Map<String, Any>? = null
    ): FormattedMessage {
        val message = FormattedMessage()
        message.messageId = translationKey
        message.markupEnabled = false

        // Add parameters if provided
        if (params != null && params.isNotEmpty()) {
            message.params = hashMapOf()
            for ((key, value) in params) {
                when (value) {
                    is String -> message.params!![key] = StringParamValue().apply { this.value = value }
                    is Int -> message.params!![key] = IntParamValue().apply { this.value = value }
                    is Double -> message.params!![key] = DoubleParamValue().apply { this.value = value }
                    is Float -> message.params!![key] = DoubleParamValue().apply { this.value = value.toDouble() }
                    is Long -> message.params!![key] = LongParamValue().apply { this.value = value }
                    is Boolean -> message.params!![key] = BoolParamValue().apply { this.value = value }
                }
            }
        }

        // Initialize @Nonnull MaybeBool fields to prevent client crashes
        message.bold = MaybeBool.Null
        message.italic = MaybeBool.Null
        message.monospace = MaybeBool.Null
        message.underlined = MaybeBool.Null

        return message
    }

    /**
     * Sends an event title HUD with translation support.
     *
     * @param playerRef Player to send HUD to
     * @param primaryKey Translation key for primary title
     * @param primaryParams Parameters for primary title translation
     * @param secondaryKey Translation key for secondary title (optional)
     * @param secondaryParams Parameters for secondary title translation
     * @param duration How long the title should display (in seconds)
     * @param isMajor Whether this is a major title (interrupts gameplay)
     * @param systemName Name of calling system (for error logging)
     * @return true if packet was sent successfully, false otherwise
     */
    fun sendTranslatedEventTitle(
        playerRef: PlayerRef,
        primaryKey: String,
        primaryParams: Map<String, Any>? = null,
        secondaryKey: String? = null,
        secondaryParams: Map<String, Any>? = null,
        duration: Float = 1.0f,
        isMajor: Boolean = false,
        systemName: String
    ): Boolean {
        return try {
            val titlePacket = ShowEventTitle()

            titlePacket.fadeInDuration = 0.0f
            titlePacket.fadeOutDuration = 0.0f
            titlePacket.duration = duration
            titlePacket.isMajor = isMajor
            titlePacket.icon = null

            // Set primary title with translation
            titlePacket.primaryTitle = createTranslatedMessage(primaryKey, primaryParams)

            // Set secondary title if provided
            titlePacket.secondaryTitle = if (secondaryKey != null) {
                createTranslatedMessage(secondaryKey, secondaryParams)
            } else {
                null
            }

            playerRef.packetHandler.write(titlePacket)
            true
        } catch (e: Exception) {
            Log.error(systemName, "Error sending translated event title HUD: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
