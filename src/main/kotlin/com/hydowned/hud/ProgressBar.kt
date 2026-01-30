package com.hydowned.hud

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder

/**
 * UI component that displays a progress bar.
 *
 * Shows a textured progress bar with configurable:
 * - Fill percentage (0.0 to 1.0)
 * - Background texture
 * - Fill texture
 * - Optional effect texture
 * - Width and height
 */
class ProgressBar(
    private val id: String,
    value: Float,
    private val bar: String,
    private val barFill: String
) : UIComponent {

    private val value: Float = value.coerceIn(0.0f, 1.0f)
    private var width: Int = 450
    private var height: Int = 20
    private var barEffect: String = "Hud/ProcessingBarEffect.png"

    /**
     * Sets the width of the progress bar.
     *
     * @param width The width in pixels
     * @return This instance for chaining
     */
    fun setWidth(width: Int): ProgressBar {
        this.width = width
        return this
    }

    /**
     * Sets the height of the progress bar.
     *
     * @param height The height in pixels
     * @return This instance for chaining
     */
    fun setHeight(height: Int): ProgressBar {
        this.height = height
        return this
    }

    /**
     * Sets the effect texture for the progress bar.
     *
     * @param barEffect Path to the effect texture
     * @return This instance for chaining
     */
    fun setBarEffect(barEffect: String): ProgressBar {
        this.barEffect = barEffect
        return this
    }

    /**
     * Builds the progress bar UI component.
     *
     * Injects Hytale UI DSL code to create a Group with a ProgressBar element.
     */
    override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
        // Build the UI DSL for a progress bar
        val dsl = """
            Group {
              Anchor: (Width: $width, Height: $height);
              Background: "$bar";

              ProgressBar #$id {
                Value: $value;
                BarTexturePath: "$barFill";
                EffectTexturePath: "$barEffect";
                EffectWidth: 200;
                EffectHeight: 58;
                EffectOffset: 74;
              }
            }
        """.trimIndent()

        ui.appendInline(selector, dsl)

        // Add height to anchor for stacking
        anchor.addHeight(10)
    }
}
