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
import com.hydowned.util.Log
import java.util.logging.Level


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
        if (Log.isEnabled(Level.FINER)) {
            Log.finer("CollisionDisable", "============================================")
            Log.finer("CollisionDisable", "Disabling character collision for downed player")
        }

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

                Log.finer("CollisionDisable", "Disabled character collisions (blocks still collide)")
            } else {
                Log.warning("CollisionDisable", "CollisionResultComponent not found")
            }
        } catch (e: Exception) {
            Log.warning("CollisionDisable", "Failed to disable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // Remove RespondToHit component to prevent knockback reactions
        try {
            val hadRespondToHit = commandBuffer.getComponent(ref, RespondToHit.getComponentType()) != null
            if (hadRespondToHit) {
                commandBuffer.tryRemoveComponent(ref, RespondToHit.getComponentType())
                Log.finer("CollisionDisable", "Removed RespondToHit (no knockback)")
            }
        } catch (e: Exception) {
            Log.warning("CollisionDisable", "Failed to remove RespondToHit: ${e.message}")
            e.printStackTrace()
        }

        Log.finer("CollisionDisable", "============================================")
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
        if (Log.isEnabled(Level.FINER)) {
            Log.finer("CollisionDisable", "============================================")
            Log.finer("CollisionDisable", "Restoring character collision for player")
        }

        // Re-enable character (entity-to-entity) collisions if they were enabled before
        try {
            val collisionResult = commandBuffer.getComponent(ref, CollisionResultComponent.getComponentType())
            if (collisionResult != null && component.hadCollisionEnabled) {
                // Note: API method has typo - "Collisions" instead of "Collisions"
                collisionResult.collisionResult.enableCharacterCollsions()
                Log.finer("CollisionDisable", "Re-enabled character collisions")
            }
        } catch (e: Exception) {
            Log.warning("CollisionDisable", "Failed to re-enable character collisions: ${e.message}")
            e.printStackTrace()
        }

        // Restore RespondToHit component
        try {
            commandBuffer.ensureComponent(ref, RespondToHit.getComponentType())
            Log.finer("CollisionDisable", "Restored RespondToHit (knockback enabled)")
        } catch (e: Exception) {
            Log.warning("CollisionDisable", "Failed to restore RespondToHit: ${e.message}")
            e.printStackTrace()
        }

        Log.finer("CollisionDisable", "============================================")
    }
}
