package com.hydowned.hud

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder

/**
 * UI component that displays a text label.
 *
 * Shows a centered text label with configurable:
 * - Message (with translation support)
 * - Font size
 * - Optional background color
 * - Height
 */
class LabelValue(
    private val id: String,
    private val value: Message,
    private var fontSize: Int = 18
) : InfoValue {

    private var height: Int = fontSize * 2
    private var backgroundColor: String? = null

    /**
     * Sets the font size.
     *
     * @param fontSize The font size in pixels
     * @return This instance for chaining
     */
    fun fontSize(fontSize: Int): LabelValue {
        this.fontSize = fontSize
        return this
    }

    /**
     * Sets the height of the label component.
     *
     * @param height The height in pixels
     * @return This instance for chaining
     */
    fun setHeight(height: Int): LabelValue {
        this.height = height
        return this
    }

    /**
     * Sets the background color of the label.
     *
     * @param backgroundColor Color string (e.g., "#000000(0.1)" for semi-transparent black)
     * @return This instance for chaining
     */
    fun setBackgroundColor(backgroundColor: String): LabelValue {
        this.backgroundColor = backgroundColor
        return this
    }

    /**
     * Builds the label UI component.
     *
     * Injects Hytale UI DSL code to create a Label element with centered text.
     */
    override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
        // Build the UI DSL for a label with optional background
        val backgroundDsl = if (backgroundColor != null) {
            "Background: $backgroundColor;\n"
        } else {
            ""
        }

        val dsl = """
            Label #$id {
              Style: LabelStyle(FontSize: $fontSize, Alignment: Center);
            $backgroundDsl}
        """.trimIndent()

        ui.appendInline(selector, dsl)

        // Set the message content (supports translation)
        ui.set("$selector #$id.TextSpans", value)

        // Add height to anchor for stacking
        anchor.addHeight(height)
    }
}
