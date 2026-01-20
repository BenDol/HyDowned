package com.hydowned.util

import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


class AnimationManager(private val config: DownedConfig) {

    // Cache for storing original animations to restore later
    private val originalAnimations = mutableMapOf<Any, Any>()

    fun setDownedAnimation(player: Any) {
        // TODO: Implement with actual Hytale animation API
        // This is a placeholder that will need to be updated with the actual API
        // Example pseudocode:
        // val animationType = if (config.isLayingAnimation) {
        //     AnimationType.LAYING
        // } else {
        //     AnimationType.CRAWLING
        // }
        // originalAnimations[player] = player.currentAnimation
        // player.setAnimation(animationType)

        println("Setting downed animation for player (type: ${if (config.isLayingAnimation) "LAYING" else "CRAWLING"})")
    }

    fun restoreNormalAnimation(player: Any) {
        // TODO: Implement with actual Hytale animation API
        // Example pseudocode:
        // val originalAnimation = originalAnimations.remove(player)
        // if (originalAnimation != null) {
        //     player.setAnimation(originalAnimation)
        // } else {
        //     player.resetToDefaultAnimation()
        // }

        println("Restoring normal animation for player")
    }

    fun cleanup() {
        originalAnimations.clear()
    }
}
