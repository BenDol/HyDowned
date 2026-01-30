package com.hydowned.hud

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder

/**
 * UI component that groups multiple UIComponent components together.
 *
 * Allows composing complex UI structures by combining multiple
 * components into a single logical unit.
 */
class GroupValue(
    private val id: String,
    private val components: List<UIComponent>
) : UIComponent {

    /**
     * Builds all child components in sequence.
     *
     * Each child component is built with the same selector and anchor,
     * allowing them to stack vertically.
     */
    override fun build(ui: UICommandBuilder, anchor: AnchorBuilder, selector: String) {
        // Build all child components
        for (component in components) {
            if (component != UIComponent.EMPTY) {
                component.build(ui, anchor, selector)
            }
        }
    }
}
