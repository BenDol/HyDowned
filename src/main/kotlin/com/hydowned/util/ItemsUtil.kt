package com.hydowned.util

import com.hypixel.hytale.server.core.inventory.ItemStack

/**
 * Utility functions for working with items.
 */
object ItemsUtil {

    /**
     * Checks if an ItemStack is a weapon using the Hytale API.
     *
     * @param itemStack The ItemStack to check
     * @return true if the item is a weapon, false otherwise
     */
    fun isWeapon(itemStack: ItemStack?): Boolean {
        if (itemStack == null || itemStack.itemId == "Empty") {
            return false
        }

        return itemStack.item.weapon != null
    }

    /**
     * Checks if an ItemStack is a tool using the Hytale API.
     *
     * @param itemStack The ItemStack to check
     * @return true if the item is a tool, false otherwise
     */
    fun isTool(itemStack: ItemStack?): Boolean {
        if (itemStack == null || itemStack.itemId == "Empty") {
            return false
        }

        return itemStack.item.tool != null
    }

    /**
     * Checks if an ItemStack is a weapon or tool (anything with an attack animation).
     *
     * This is useful for determining if a player has an item that causes animation issues
     * when they go down.
     *
     * @param itemStack The ItemStack to check
     * @return true if the item is a weapon or tool, false otherwise
     */
    fun isWeaponOrTool(itemStack: ItemStack?): Boolean {
        return isWeapon(itemStack) || isTool(itemStack)
    }
}
