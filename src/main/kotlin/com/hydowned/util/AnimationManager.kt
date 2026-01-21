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
        // originalAnimations[player] = player.currentAnimation
        // player.setAnimation(AnimationType.DEATH)

        Log.verbose("Animation", "Setting downed animation for player")
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

        Log.verbose("Animation", "Restoring normal animation for player")
    }

    fun cleanup() {
        originalAnimations.clear()
    }
}
