package com.hydowned.hud

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hydowned.ModPlugin
import com.hydowned.player.aspect.ModPlayer
import com.hydowned.util.StringUtil
import kotlin.math.roundToInt

/**
 * Custom HUD displayed to downed players showing:
 * - Death timer countdown with progress bar
 * - Revive progress when being revived
 * - Instructions for other players
 *
 * Updates dynamically as state changes via the updateHud() method.
 */
class DownedHud(playerRef: PlayerRef) : CustomUIHud(playerRef) {

    private var info: InfoBuilder? = null
    private var visible: Boolean = true

    /**
     * Builds the HUD using the UICommandBuilder.
     *
     * This method is called automatically by show() and constructs the
     * UI DOM structure with all components.
     */
    override fun build(ui: UICommandBuilder) {
        customBuild(ui)
    }

    /**
     * Internal build method that constructs the HUD UI.
     *
     * Loads the base UI file and adds all InfoValue components.
     */
    private fun customBuild(ui: UICommandBuilder) {
        val currentInfo = info
        if (currentInfo != null && currentInfo.canDisplay()) {
            // Load base UI structure
            ui.append("Hud/Downed.ui")

            // Create anchor for positioning (bottom-center of screen)
            val anchorBuilder = AnchorBuilder()
                .setBottom(180)  // 180px from bottom
                .setHorizontal(0)  // Centered horizontally

            // Build all non-empty components
            currentInfo.values()
                .filter { it != InfoValue.EMPTY }
                .forEach { it.build(ui, anchorBuilder, "#DownedInfo") }

            // Set the final anchor position
            ui.setObject("#Downed.Anchor", anchorBuilder.build())
        }
    }

    /**
     * Updates the HUD with current state and triggers a refresh.
     *
     * @param downed The downed player whose state to display
     * @param player The viewer seeing this HUD
     */
    fun updateHud(downed: ModPlayer, player: ModPlayer) {
        this.info = InfoBuilder()

        if (!visible) {
            return
        }

        val managers = ModPlugin.instance!!.managers
        val downable = downed.asDownable

        // Calculate progress
        val currentTime = downable.getDownDuration() - downable.getTimeRemaining()
        val totalTime = downable.getDownDuration()
        var progress = (currentTime / totalTime).toFloat()
        progress = progress.coerceIn(0.0f, 1.0f)

        if (progress > 0.99) {
            return
        }

        if (!downable.isDowned()) {
            return
        }

        // If player is neither downed, reviving, nor carrying - show "press to revive" message
        if (!managers.downManager.isDowned(player.asDownable) &&
            !managers.reviveManager.isReviving(player.asReviver)) {
            val press = Message.translation("hydowned.hud.press_to_revive")
                .param("user", downable.getDisplayName())
                .param("key", Message.translation("client.settings.bindings.Crouch"))

            info!!.set("Message") { id ->
                val labelValue = LabelValue(id, press, 20)
                labelValue.setBackgroundColor("#000000(0.1)")
                labelValue
            }
            return
        }

        // Determine message and progress bar
        val message: Message
        val barFill: String
        val finalProgress: Float

        if (!managers.reviveManager.isBeingRevived(downable)) {
            // Show death timer
            val idx = 9 - (progress * 2.0f).roundToInt()
            barFill = "Hud/DownedBarFill$idx.png"
            message = if (downed == player) {
                Message.translation("hydowned.hud.downed_time")
                    .param("time", StringUtil.formatTime(downable.getTimeRemaining() / 20.0))
            } else {
                Message.translation("hydowned.hud.reviver_time")
                    .param("time", StringUtil.formatTime(downable.getTimeRemaining() / 20.0))
            }
            finalProgress = 1.0f - progress
        } else {
            // Show revive progress
            val reviver = managers.reviveManager.getReviverOf(downable)!!
            barFill = "Hud/DownedBarFill0.png"
            finalProgress = managers.reviveManager.getProgress(reviver).toFloat()

            if (finalProgress > 0.99) {
                return
            }

            message = if (downed == player) {
                Message.translation("hydowned.hud.reviving")
                    .param("user", reviver.getDisplayName())
            } else {
                Message.translation("hydowned.hud.reviver_reviving")
                    .param("user", downable.getDisplayName())
            }
        }

        // Show giveup progress if active
        if (downable.giveUpTicks != -1) {
            info!!.set("GiveUpGroup") { id ->
                val values = mutableListOf<InfoValue>()
                val giveupProgress = if (downable.giveUpTicks == -1) 0.0f
                    else (downable.giveUpTicks.toFloat() / downable.getMaxGiveUpTicks().toFloat()).coerceIn(0.0f, 1.0f)

                values.add(LabelValue(id + "GiveUpMessage",
                    Message.translation("hydowned.hud.giving_up"), 12))

                val giveupBar = ProgressBarValue(id + "GiveUp",
                    1.0f - giveupProgress, "Hud/GiveUpBar.png", "Hud/GiveUpBarFill.png")
                giveupBar.setBarEffect("Hud/GiveUpBarEffect.png")
                giveupBar.setHeight(12)
                giveupBar.setWidth(250)
                values.add(giveupBar)

                values.add(LabelValue(id + "Space", Message.raw("  "), 10))
                GroupValue(id, values)
            }
        }

        // Add main message
        info!!.set("Message") { id -> LabelValue(id, message, 17) }

        // Add progress bar and giveup press message
        info!!.set("Others") { id ->
            val values = mutableListOf<InfoValue>()
            values.add(ProgressBarValue(id + "Progress",
                (finalProgress * 1000.0f).roundToInt() / 1000.0f, "Hud/DownedBar.png", barFill))

            val reviver = managers.reviveManager.getReviverOf(downable)
            if (downable.giveUpTicks == -1 && reviver == null) {
                values.add(LabelValue(id + "GiveUpPress",
                    Message.translation("hydowned.hud.giveup_press")
                        .param("key", Message.translation("client.settings.bindings.Crouch")),
                    15))
            }
            GroupValue(id, values)
        }

        if (visible) {
            show()
        }
    }

    /**
     * Updates the HUD with an InfoBuilder (old method).
     */
    fun updateHud(builder: InfoBuilder) {
        this.info = builder
        if (visible) {
            show()
        }
    }

    /**
     * Sets whether the HUD should be visible.
     *
     * When set to false, the HUD won't render even if show() is called.
     *
     * @param visible true to show the HUD, false to hide it
     */
    fun setVisible(visible: Boolean) {
        this.visible = visible
    }

    /**
     * Checks if the HUD is currently visible.
     *
     * @return true if visible, false otherwise
     */
    fun isVisible(): Boolean {
        return visible
    }
}
