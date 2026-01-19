package com.hydowned.systems

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import javax.annotation.Nonnull

/**
 * System that applies visual effects when a player enters or exits downed state
 *
 * Applies:
 * - Movement speed reduction
 * - Downed animation (lying/crawling)
 * - Particle effects
 * - Sound effects
 */
class DownedVisualEffectsSystem(
    private val config: DownedConfig
) : RefSystem<EntityStore>() {

    private val query = Query.and(
        DownedComponent.getComponentType(),
        Player.getComponentType()
    )

    @Nonnull
    override fun getQuery(): Query<EntityStore> = query

    override fun onEntityAdded(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull reason: AddReason,
        @Nonnull store: Store<EntityStore>,
        @Nonnull commandBuffer: CommandBuffer<EntityStore>
    ) {
        println("[HyDowned] Applying visual effects for downed state...")

        // TODO: Apply downed animation based on config.downedAnimationType
        // AnimationUtils.playAnimation(ref, AnimationSlot.Status, "downed_lying", true, commandBuffer)

        // TODO: Apply movement speed reduction
        // val movementManager = ...
        // movementManager.setSpeedMultiplier(ref, config.downedSpeedMultiplier)

        // TODO: Apply particle effects if enabled
        // if (config.enableParticles) {
        //     // Spawn red/downed particles around player
        // }

        // TODO: Apply sound effects if enabled
        // if (config.enableSounds) {
        //     // Play downed/hurt sound
        // }

        // TODO: Send action bar message to player
        // val player = commandBuffer.getComponent(ref, Player.getComponentType())
        // player?.sendMessage("You are downed! Wait for revival or ${config.downedTimerSeconds}s")

        println("[HyDowned] ✓ Visual effects applied (placeholder)")
    }

    override fun onEntityRemove(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull reason: RemoveReason,
        @Nonnull store: Store<EntityStore>,
        @Nonnull commandBuffer: CommandBuffer<EntityStore>
    ) {
        println("[HyDowned] Removing visual effects from downed state...")

        // TODO: Restore normal animation
        // AnimationUtils.playAnimation(ref, AnimationSlot.Status, "idle", true, commandBuffer)

        // TODO: Restore normal movement speed
        // movementManager.setSpeedMultiplier(ref, 1.0f)

        // TODO: Clear particle effects

        // TODO: Clear sound effects

        println("[HyDowned] ✓ Visual effects removed (placeholder)")
    }
}
