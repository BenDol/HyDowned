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

    private var ui: UIBuilder? = null
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
     * Loads the base UI file and adds all UiComponent components.
     */
    private fun customBuild(ui: UICommandBuilder) {
        val currentUi = this@DownedHud.ui
        if (currentUi != null && currentUi.canDisplay()) {
            // Load base UI structure
            ui.append("Hud/Downed.ui")

            // Create anchor for positioning (bottom-center of screen)
            val anchorBuilder = AnchorBuilder()
                .setTop(150)
                .setHorizontal(0)  // centered horizontally

            // Build all non-empty components
            currentUi.components()
                .filter { it != UIComponent.EMPTY }
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
        this.ui = UIBuilder()

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

            // Create a custom component positioned at bottom of screen (separate from main HUD)
            ui?.set("PressToRevive") { id ->
                object : UIComponent {
                    override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
                        // Position at bottom center of screen
                        val dsl = """
                            Label #${id} {
                              Anchor: (Bottom: 0, Horizontal: 0);
                              Style: LabelStyle(FontSize: 15, Alignment: Center);
                            }
                        """.trimIndent()

                        // Append directly to #Downed (root), not #DownedInfo
                        ui.appendInline("#Downed", dsl)
                        ui.set("#Downed #${id}.TextSpans", press)
                        // Don't modify the anchor - this is positioned absolutely
                    }
                }
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

        // Add progress bar with message label overlaid on top
        ui?.set("Others") { id ->
            val values = mutableListOf<UIComponent>()

            // Add combined progress bar + overlaid message as single component
            values.add(object : UIComponent {
                override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
                    val dsl = """
                        Group {
                          Anchor: (Width: 450, Height: 20);
                          Background: "Hud/DownedBar.png";

                          ProgressBar #${id}Progress {
                            Value: ${(finalProgress * 1000.0f).roundToInt() / 1000.0f};
                            BarTexturePath: "$barFill";
                            EffectTexturePath: "Hud/ProcessingBarEffect.png";
                            EffectWidth: 200;
                            EffectHeight: 58;
                            EffectOffset: 74;
                          }

                          Label #${id}Message {
                            Anchor: (Top: 0, Left: 0, Right: 0);
                            Style: LabelStyle(FontSize: 15, Alignment: Center);
                          }
                        }
                    """.trimIndent()

                    ui.appendInline(selector, dsl)
                    ui.set("$selector #${id}Message.TextSpans", message)
                    anchor.addHeight(10)
                }
            })

            val reviver = managers.reviveManager.getReviverOf(downable)

            // Add spacing below progress bar
            values.add(Label(id + "Spacer", Message.raw(""), 3))

            // Show give up progress if active, otherwise show give up press message
            if (downable.giveUpTicks != -1) {
                // Player is giving up - show progress
                val giveUpProgress = (downable.giveUpTicks.toFloat() / downable.getMaxGiveUpTicks().toFloat()).coerceIn(0.0f, 1.0f)

                values.add(Label(id + "GiveUpMessage",
                    Message.translation("hydowned.hud.giving_up"), 12))

                val giveUpBar = ProgressBar(id + "GiveUp",
                    1.0f - giveUpProgress, "Hud/GiveUpBar.png", "Hud/GiveUpBarFill.png")
                giveUpBar.setBarEffect("Hud/GiveUpBarEffect.png")
                giveUpBar.setHeight(12)
                giveUpBar.setWidth(180)
                values.add(giveUpBar)
            } else if (reviver == null) {
                // Not giving up and not being revived - show give up press message
                values.add(Label(id + "GiveUpPress",
                    Message.translation("hydowned.hud.giveup_press")
                        .param("key", Message.translation("client.settings.bindings.Crouch")),
                    12))
            }
            GroupValue(id, values)
        }

        if (visible) {
            show()
        }
    }

    /**
     * Updates the HUD with an UiBuilder (old method).
     */
    fun updateHud(builder: UIBuilder) {
        this.ui = builder
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
