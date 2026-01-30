package com.hydowned.hud

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder

/**
 * Base interface for all custom HUD UI components.
 *
 * Components implement this interface to define how they should be built
 * and added to the HUD using the UICommandBuilder.
 */
interface UIComponent {

    /**
     * Builds this UI component and adds it to the HUD.
     *
     * @param ui The UICommandBuilder to add commands to
     * @param anchor The AnchorBuilder for positioning
     * @param selector The CSS-like selector for the parent element
     */
    fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String)

    companion object {
        /**
         * Empty placeholder component that does nothing.
         * Used as a default value to indicate "no component".
         */
        val EMPTY: UIComponent = object : UIComponent {
            override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
                // No-op
            }
        }
    }
}
