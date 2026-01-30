package com.hydowned.hud

/**
 * Builder pattern for managing UI components in a custom HUD.
 *
 * Uses a LinkedHashMap to maintain insertion order of components.
 * Components are created using factory functions and stored by ID.
 */
class UIBuilder {

    private var icon: UIComponent = UIComponent.EMPTY
    private val components: MutableMap<String, UIComponent> = linkedMapOf()

    /**
     * Sets a component using a factory function.
     *
     * The factory function receives the ID and returns the component instance.
     * This allows lazy creation of components.
     *
     * @param id Unique identifier for this component
     * @param factory Function that creates the component given the ID
     */
    fun <T : UIComponent> set(id: String, factory: (String) -> T) {
        components[id] = factory(id)
    }

    /**
     * Gets a component by ID.
     *
     * @param id The component identifier
     * @return The component, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : UIComponent> get(id: String): T? {
        return components[id] as? T
    }

    /**
     * Returns all stored components as a map.
     *
     * @return Map of component ID to InfoValue
     */
    fun componentMap(): Map<String, UIComponent> {
        return components
    }

    /**
     * Returns all component values as a sequence for iteration.
     *
     * @return Sequence of all InfoValue components
     */
    fun components(): Sequence<UIComponent> {
        return components.values.asSequence()
    }

    /**
     * Gets the icon component.
     *
     * @return The icon component, or EMPTY if not set
     */
    fun getIcon(): UIComponent {
        return icon
    }

    /**
     * Sets the icon component.
     *
     * @param icon The icon component to display
     */
    fun setIcon(icon: UIComponent) {
        this.icon = icon
    }

    /**
     * Checks if this builder has any displayable content.
     *
     * Returns true if there's an icon or at least one non-empty component.
     *
     * @return true if there's content to display
     */
    fun canDisplay(): Boolean {
        return getIcon() != UIComponent.EMPTY || components.values.any { it != UIComponent.EMPTY }
    }
}
