package com.hydowned.hud

/**
 * Builder pattern for managing UI components in a custom HUD.
 *
 * Uses a LinkedHashMap to maintain insertion order of components.
 * Components are created using factory functions and stored by ID.
 */
class InfoBuilder {

    private var icon: InfoValue = InfoValue.EMPTY
    private val infos: MutableMap<String, InfoValue> = linkedMapOf()

    /**
     * Sets a component using a factory function.
     *
     * The factory function receives the ID and returns the component instance.
     * This allows lazy creation of components.
     *
     * @param id Unique identifier for this component
     * @param factory Function that creates the component given the ID
     */
    fun <T : InfoValue> set(id: String, factory: (String) -> T) {
        infos[id] = factory(id)
    }

    /**
     * Gets a component by ID.
     *
     * @param id The component identifier
     * @return The component, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : InfoValue> get(id: String): T? {
        return infos[id] as? T
    }

    /**
     * Returns all stored components as a map.
     *
     * @return Map of component ID to InfoValue
     */
    fun infos(): Map<String, InfoValue> {
        return infos
    }

    /**
     * Returns all component values as a sequence for iteration.
     *
     * @return Sequence of all InfoValue components
     */
    fun values(): Sequence<InfoValue> {
        return infos.values.asSequence()
    }

    /**
     * Gets the icon component.
     *
     * @return The icon component, or EMPTY if not set
     */
    fun getIcon(): InfoValue {
        return icon
    }

    /**
     * Sets the icon component.
     *
     * @param icon The icon component to display
     */
    fun setIcon(icon: InfoValue) {
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
        return getIcon() != InfoValue.EMPTY || infos.values.any { it != InfoValue.EMPTY }
    }
}
