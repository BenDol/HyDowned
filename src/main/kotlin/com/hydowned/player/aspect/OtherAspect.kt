package com.hydowned.player.aspect

import com.hydowned.aspect.Aspect

/**
 * Aggressor for non-player sources (environment, ai, etc.)
 */
class OtherAspect(private val displayName: String) : Aspect {
    override fun getDisplayName(): String = displayName

    companion object {
        fun create(name: String): OtherAspect {
            return OtherAspect(name)
        }
    }
}
