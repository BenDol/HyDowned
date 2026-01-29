package com.hydowned.hud

import com.hypixel.hytale.server.core.ui.Anchor
import com.hypixel.hytale.server.core.ui.Value

/**
 * Builder for creating Anchor objects used in UI positioning.
 *
 * Provides a fluent API for setting position and size properties,
 * then builds the final Anchor object for use with UICommandBuilder.
 */
class AnchorBuilder {

    private var left: Int? = null
    private var right: Int? = null
    private var top: Int? = null
    private var bottom: Int? = null
    private var height: Int? = null
    private var full: Int? = null
    private var horizontal: Int? = null
    private var vertical: Int? = null
    private var width: Int? = null
    private var minWidth: Int? = null
    private var maxWidth: Int? = null

    /**
     * Adds to the current height value.
     * Used for stacking UI elements vertically.
     *
     * @param height The height to add
     */
    fun addHeight(height: Int) {
        this.height = (this.height ?: 0) + height
    }

    /**
     * Ensures height is at least the specified value.
     * Only updates if current height is less than the specified value.
     *
     * @param height The minimum height
     */
    fun ensureHeight(height: Int) {
        val currentHeight = this.height
        if (currentHeight == null || currentHeight < height) {
            this.height = height
        }
    }

    /**
     * Sets the left position.
     *
     * @param left Distance from left edge
     * @return This builder for chaining
     */
    fun setLeft(left: Int?): AnchorBuilder {
        this.left = left
        return this
    }

    /**
     * Sets the right position.
     *
     * @param right Distance from right edge
     * @return This builder for chaining
     */
    fun setRight(right: Int?): AnchorBuilder {
        this.right = right
        return this
    }

    /**
     * Sets the top position.
     *
     * @param top Distance from top edge
     * @return This builder for chaining
     */
    fun setTop(top: Int?): AnchorBuilder {
        this.top = top
        return this
    }

    /**
     * Sets the bottom position.
     *
     * @param bottom Distance from bottom edge
     * @return This builder for chaining
     */
    fun setBottom(bottom: Int?): AnchorBuilder {
        this.bottom = bottom
        return this
    }

    /**
     * Sets the height.
     *
     * @param height The height value
     * @return This builder for chaining
     */
    fun setHeight(height: Int?): AnchorBuilder {
        this.height = height
        return this
    }

    /**
     * Sets the full property.
     *
     * @param full The full value
     * @return This builder for chaining
     */
    fun setFull(full: Int?): AnchorBuilder {
        this.full = full
        return this
    }

    /**
     * Sets the horizontal center position.
     *
     * @param horizontal Horizontal offset from center (0 = center)
     * @return This builder for chaining
     */
    fun setHorizontal(horizontal: Int?): AnchorBuilder {
        this.horizontal = horizontal
        return this
    }

    /**
     * Sets the vertical center position.
     *
     * @param vertical Vertical offset from center (0 = center)
     * @return This builder for chaining
     */
    fun setVertical(vertical: Int?): AnchorBuilder {
        this.vertical = vertical
        return this
    }

    /**
     * Sets the width.
     *
     * @param width The width value
     * @return This builder for chaining
     */
    fun setWidth(width: Int?): AnchorBuilder {
        this.width = width
        return this
    }

    /**
     * Sets the minimum width.
     *
     * @param minWidth The minimum width value
     * @return This builder for chaining
     */
    fun setMinWidth(minWidth: Int?): AnchorBuilder {
        this.minWidth = minWidth
        return this
    }

    /**
     * Sets the maximum width.
     *
     * @param maxWidth The maximum width value
     * @return This builder for chaining
     */
    fun setMaxWidth(maxWidth: Int?): AnchorBuilder {
        this.maxWidth = maxWidth
        return this
    }

    /**
     * Builds the final Anchor object.
     *
     * Only sets non-null properties on the Anchor.
     *
     * @return The configured Anchor instance
     */
    fun build(): Anchor {
        val anchor = Anchor()

        left?.let { anchor.setLeft(Value.of(it)) }
        right?.let { anchor.setRight(Value.of(it)) }
        top?.let { anchor.setTop(Value.of(it)) }
        bottom?.let { anchor.setBottom(Value.of(it)) }
        height?.let { anchor.setHeight(Value.of(it)) }
        full?.let { anchor.setFull(Value.of(it)) }
        horizontal?.let { anchor.setHorizontal(Value.of(it)) }
        vertical?.let { anchor.setVertical(Value.of(it)) }
        width?.let { anchor.setWidth(Value.of(it)) }
        minWidth?.let { anchor.setMinWidth(Value.of(it)) }
        maxWidth?.let { anchor.setMaxWidth(Value.of(it)) }

        return anchor
    }
}
