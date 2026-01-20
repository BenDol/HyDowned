package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig

/**
 * Disables character (entity-to-entity) collision for downed players.
 *
 * Uses CollisionResultComponent to selectively disable player-to-player and
 * entity-to-entity collisions while keeping block/wall collision active.
 *
 * Also removes RespondToHit component to prevent knockback reactions.
 *
 * Works in both SCALE and INVISIBLE modes.
 */
class DownedCollisionDisableSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        println("[HyDowned] [Collision] ============================================")
        println("[HyDowned] [Collision] Disabling character collision for downed player")

        // Disable character (entity-to-entity) collisions using CollisionResultComponent
        try {
            val collisionResult = commandBuffer.getComponent(ref, CollisionResultComponent.getComponentType())
            if (collisionResult != null) {
                // Check current state before disabling
                val wasCheckingCharacterCollisions = collisionResult.collisionResult.isCheckingForCharacterCollisions

                // Disable character (entity-to-entity) collisions while keeping block collisions
                collisionResult.collisionResult.disableCharacterCollisions()

                // Store original state for restoration
                component.hadCollisionEnabled = wasCheckingCharacterCollisions

                println("[HyDowned] [Collision] ✓ Disabled character collisions (blocks still collide)")
            } else {
                println("[HyDowned] [Collision] ⚠ CollisionResultComponent not found")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Collision] ⚠ Failed to disable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // Remove RespondToHit component to prevent knockback reactions
        try {
            val hadRespondToHit = commandBuffer.getComponent(ref, RespondToHit.getComponentType()) != null
            if (hadRespondToHit) {
                commandBuffer.tryRemoveComponent(ref, RespondToHit.getComponentType())
                println("[HyDowned] [Collision] ✓ Removed RespondToHit (no knockback)")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Collision] ⚠ Failed to remove RespondToHit: ${e.message}")
            e.printStackTrace()
        }

        println("[HyDowned] [Collision] ============================================")
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        println("[HyDowned] [Collision] ============================================")
        println("[HyDowned] [Collision] Restoring character collision for player")

        // Re-enable character (entity-to-entity) collisions if they were enabled before
        try {
            val collisionResult = commandBuffer.getComponent(ref, CollisionResultComponent.getComponentType())
            if (collisionResult != null && component.hadCollisionEnabled) {
                // Note: API method has typo - "Collsions" instead of "Collisions"
                collisionResult.collisionResult.enableCharacterCollsions()
                println("[HyDowned] [Collision] ✓ Re-enabled character collisions")
            }
        } catch (e: Exception) {
            println("[HyDowned] [Collision] ⚠ Failed to re-enable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // Restore RespondToHit component
        try {
            commandBuffer.ensureComponent(ref, RespondToHit.getComponentType())
            println("[HyDowned] [Collision] ✓ Restored RespondToHit (knockback enabled)")
        } catch (e: Exception) {
            println("[HyDowned] [Collision] ⚠ Failed to restore RespondToHit: ${e.message}")
            e.printStackTrace()
        }

        println("[HyDowned] [Collision] ============================================")
    }
}
