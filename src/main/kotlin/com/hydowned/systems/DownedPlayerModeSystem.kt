package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesSystems
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log
import com.hydowned.network.DownedStateTracker
import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent

/**
 * Handles PLAYER mode by putting the downed player into sleep/laying state.
 *
 * PLAYER mode approach:
 * - When player becomes downed: Play death animation, set player to sleeping state
 * - Player character lays down in place (no phantom body needed)
 * - Camera attaches to player's body looking down from above
 * - Movement state is locked to sleeping every tick to prevent client from overriding it
 *
 * This is different from PHANTOM mode where:
 * - Player goes invisible/tiny and can move around
 * - A phantom body NPC is spawned to show the downed location
 */
class DownedPlayerModeSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType(),
        MovementStatesComponent.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only - skip for PHANTOM mode
        if (!config.usePlayerMode) {
            return
        }

        Log.finer("PlayerMode", "============================================")
        Log.finer("PlayerMode", "PLAYER MODE: Putting player into sleep state")

        try {
            // CRITICAL: Stop ALL animations on ALL slots first to clear weapon/item animations
            // This prevents weapon animations from interfering with the Death animation
            try {
                AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, commandBuffer)
                AnimationUtils.stopAnimation(ref, AnimationSlot.Action, commandBuffer)
                AnimationUtils.stopAnimation(ref, AnimationSlot.Emote, commandBuffer)
                Log.finer("PlayerMode", "Cleared all animation slots")
            } catch (e: Exception) {
                Log.warning("PlayerMode", "Failed to clear animations: ${e.message}")
            }

            // Play death animation on the player IMMEDIATELY
            AnimationUtils.playAnimation(
                ref,
                AnimationSlot.Movement,
                "Death",
                true, // sendToSelf
                commandBuffer
            )
            Log.finer("PlayerMode", "Playing Death animation on player")

            // Set movement state to sleeping (laying down)
            val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
            if (movementStatesComponent != null) {
                val states = movementStatesComponent.movementStates
                val sentStates = movementStatesComponent.sentMovementStates

                // Continuously force sleeping state every tick
                // This prevents the server's movement state system from reverting to idle
                if (!states.sleeping || states.idle || states.walking || states.running || states.sprinting) {
                    states.sleeping = true
                    states.idle = false
                    states.horizontalIdle = false
                    states.walking = false
                    states.running = false
                    states.sprinting = false
                    states.jumping = false
                    states.falling = false
                    states.crouching = false
                    states.forcedCrouching = false
                    states.climbing = false
                    states.flying = false
                    states.swimming = false
                    states.swimJumping = false
                    states.mantling = false
                    states.sliding = false
                    states.mounting = false
                    states.rolling = false
                    states.sitting = false
                    states.gliding = false

                    // Force sentStates to be different so MovementStatesSystems.TickingSystem detects change
                    sentStates.sleeping = false
                    sentStates.idle = true
                }
                Log.finer("PlayerMode", "Set player to sleeping state (laying down)")
            } else {
                Log.warning("PlayerMode", "MovementStatesComponent not found")
            }

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to set player mode: ${e.message}")
            e.printStackTrace()
        }

        Log.finer("PlayerMode", "============================================")
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
        // PLAYER mode only
        if (!config.usePlayerMode) {
            return
        }

        Log.finer("PlayerMode", "============================================")
        Log.finer("PlayerMode", "PLAYER MODE: Removing sleep state from player")

        try {
            // Check if player is at 0 HP (death scenario) or >0 HP (revive scenario)
            val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
            val healthStat = entityStatMap?.get(DefaultEntityStatTypes.getHealth())
            val currentHealth = healthStat?.get() ?: 0.0f

            val isDying = currentHealth <= 0.0f

            if (isDying) {
                // Player is dying - DO NOT reset movement states
                // The respawn system will handle movement state initialization
                Log.finer("PlayerMode", "Player is dying (0 HP) - skipping movement state reset (respawn will handle it)")
            } else {
                // Player is being revived - reset movement state to normal
                val movementStatesComponent = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType())
                if (movementStatesComponent != null) {
                    // CRITICAL: Must use DIFFERENT objects for change detection
                    // If both are the same object, ECS won't detect change and won't send to player's client
                    val newStates = MovementStates()
                    newStates.sleeping = false
                    newStates.idle = true  // Default to idle when standing
                    newStates.onGround = true

                    val oldSentStates = MovementStates()
                    oldSentStates.sleeping = true  // Old state was sleeping
                    oldSentStates.idle = false
                    oldSentStates.onGround = true

                    movementStatesComponent.movementStates = newStates
                    movementStatesComponent.sentMovementStates = oldSentStates
                    Log.finer("PlayerMode", "Reset player to normal movement state (revive scenario)")
                }
            }

            // Stop death animation (do this in both scenarios)
            AnimationUtils.stopAnimation(
                ref,
                AnimationSlot.Movement,
                commandBuffer
            )
            Log.finer("PlayerMode", "Stopped Death animation")

            // Remove ActiveAnimationComponent if present
            try {
                commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent.getComponentType())
                Log.finer("PlayerMode", "Removed ActiveAnimationComponent")
            } catch (e: Exception) {
                Log.finer("PlayerMode", "No ActiveAnimationComponent to remove (or removal failed)")
            }

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to reset player mode: ${e.message}")
            e.printStackTrace()
        }

        Log.finer("PlayerMode", "============================================")
    }
}

/**
 * Keeps downed players in sleep state by re-applying it every tick.
 * This prevents the client from overriding the sleep state with normal movement.
 *
 * Only runs in PLAYER mode.
 * Runs AFTER Hytale's MovementStatesSystems to override any state changes.
 */
class DownedPlayerModeSyncSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    companion object {
        // Cached sleeping states to avoid setting 40+ properties every tick
        private val SLEEPING_STATES = MovementStates().apply {
            sleeping = true
            idle = false
            horizontalIdle = false
            walking = false
            running = false
            sprinting = false
            jumping = false
            falling = false
            crouching = false
            forcedCrouching = false
            climbing = false
            flying = false
            swimming = false
            swimJumping = false
            mantling = false
            sliding = false
            mounting = false
            rolling = false
            sitting = false
            gliding = false
        }
    }

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        MovementStatesComponent.getComponentType()
    )

    // Run AFTER all movement systems to override any state changes they make
    private val dependencies = setOf<Dependency<EntityStore>>(
        SystemDependency(Order.AFTER, MovementStatesSystems.TickingSystem::class.java),
        SystemDependency(Order.AFTER, PlayerProcessMovementSystem::class.java)
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun getDependencies(): Set<Dependency<EntityStore>> = dependencies

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only - skip for PHANTOM mode
        if (!config.usePlayerMode) {
            return
        }

        // CRITICAL: Check state tracker - if player is not downed anymore, don't force sleeping
        // This prevents forcing sleeping during revive after state tracker is cleared
        val ref = archetypeChunk.getReferenceTo(index)
        if (!com.hydowned.network.DownedStateTracker.isDowned(ref)) {
            return
        }

        // Ensure player stays in sleeping state
        val movementStatesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType())
            ?: return

        val states = movementStatesComponent.movementStates
        val sentStates = movementStatesComponent.sentMovementStates

        // Check if any non-sleeping states are active
        val needsReset = !states.sleeping || states.idle || states.walking || states.running ||
                         states.sprinting || !sentStates.sleeping || sentStates.idle ||
                         sentStates.walking || sentStates.running || sentStates.sprinting

        if (needsReset) {
            // CRITICAL: Preserve actual falling/onGround states to prevent hovering
            // When we force sleeping, we must preserve physics-related states
            val actualFalling = states.falling
            val actualOnGround = states.onGround

            // CRITICAL: Must use DIFFERENT objects for change detection to replicate to other players
            // If both are the same object, ECS won't detect change and won't send to other clients
            val newStates = MovementStates()
            newStates.sleeping = true
            newStates.idle = false
            newStates.horizontalIdle = false
            newStates.walking = false
            newStates.running = false
            newStates.sprinting = false
            newStates.jumping = false
            newStates.falling = actualFalling  // Preserve physics
            newStates.crouching = false
            newStates.forcedCrouching = false
            newStates.climbing = false
            newStates.flying = false
            newStates.swimming = false
            newStates.swimJumping = false
            newStates.mantling = false
            newStates.sliding = false
            newStates.mounting = false
            newStates.rolling = false
            newStates.sitting = false
            newStates.gliding = false
            newStates.onGround = actualOnGround  // Preserve physics

            val oldSentStates = MovementStates()
            oldSentStates.sleeping = false
            oldSentStates.idle = true

            movementStatesComponent.movementStates = newStates
            movementStatesComponent.sentMovementStates = oldSentStates
        }

        // Capture downed location when player lands (needed for revive system)
        // Note: ref is already declared above for state tracker check
        val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
            ?: return

        // When player lands, raycast down to find actual ground surface
        if (downedComponent.downedLocation == null && states.onGround) {
            val transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
            if (transform != null) {
                val currentPos = transform.position
                var groundY: Double? = null
                val startY = currentPos.y
                val world = store.externalData.world

                // Raycast down from current position to find solid ground (check up to 5 blocks down)
                for (checkY in kotlin.math.floor(startY).toInt() downTo (kotlin.math.floor(startY) - 5).toInt()) {
                    val blockX = kotlin.math.floor(currentPos.x).toInt()
                    val blockZ = kotlin.math.floor(currentPos.z).toInt()

                    try {
                        val blockId = world.getBlock(blockX, checkY, blockZ)
                        val blockType = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getAsset(blockId)

                        if (blockType != null && blockType.material == com.hypixel.hytale.protocol.BlockMaterial.Solid) {
                            groundY = checkY + 1.0
                            Log.info("PlayerMode", "Raycast found ground at Y=$checkY, locking to Y=$groundY")
                            break
                        }
                    } catch (e: Exception) {
                        Log.warning("PlayerMode", "Failed to check block: ${e.message}")
                    }
                }

                if (groundY != null) {
                    // Snap player to ground level
                    transform.position.y = groundY
                    downedComponent.downedLocation = Vector3d(currentPos.x, groundY, currentPos.z)
                    Log.info("PlayerMode", "Player locked to ground surface: ${downedComponent.downedLocation}")
                } else {
                    // Fallback: use current position
                    downedComponent.downedLocation = Vector3d(currentPos.x, currentPos.y, currentPos.z)
                    Log.warning("PlayerMode", "No ground found, using current position: ${downedComponent.downedLocation}")
                }
            }
        }
    }
}
