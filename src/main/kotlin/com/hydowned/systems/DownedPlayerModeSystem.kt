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
import com.hypixel.hytale.protocol.ComponentUpdate
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.protocol.EntityUpdate
import com.hypixel.hytale.protocol.Equipment
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.math.shape.Box
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hydowned.components.PhantomBodyMarker
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

        try {
            // Check if player has a weapon or tool currently in hand
            val playerComponent = commandBuffer.getComponent(ref, Player.getComponentType())
            val hasWeaponOrToolInHand = if (playerComponent != null) {
                val inventory = playerComponent.inventory
                val itemInHand = inventory.itemInHand
                com.hydowned.util.ItemsUtil.isWeaponOrTool(itemInHand)
            } else {
                false
            }

            // HYBRID APPROACH: Only spawn phantom body if player has weapon/tool in hand
            // This lets them see their body laying down when they have items with attack animations
            if (hasWeaponOrToolInHand) {
                try {
                    spawnPhantomBody(ref, component, commandBuffer)
                } catch (e: Exception) {
                    Log.warning("PlayerMode", "Failed to spawn phantom body: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Clear animation slots and play death animation
            try {
                AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, commandBuffer)
                AnimationUtils.stopAnimation(ref, AnimationSlot.Action, commandBuffer)
                AnimationUtils.stopAnimation(ref, AnimationSlot.Emote, commandBuffer)
            } catch (e: Exception) {
                Log.warning("PlayerMode", "Failed to clear animations: ${e.message}")
            }

            AnimationUtils.playAnimation(
                ref,
                AnimationSlot.Movement,
                "Death",
                true, // sendToSelf
                commandBuffer
            )

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
            } else {
                Log.warning("PlayerMode", "MovementStatesComponent not found")
            }

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to set player mode: ${e.message}")
            e.printStackTrace()
        }
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

        try {
            // NOTE: Phantom body cleanup is handled by DownedCleanupHelper.cleanupDownedState()
            // which is called before DownedComponent removal during revive/death flows

            // Check if player is at 0 HP (death scenario) or >0 HP (revive scenario)
            val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
            val healthStat = entityStatMap?.get(DefaultEntityStatTypes.getHealth())
            val currentHealth = healthStat?.get() ?: 0.0f

            val isDying = currentHealth <= 0.0f

            if (!isDying) {
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
                }
            }

            // Stop death animation (do this in both scenarios)
            AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, commandBuffer)

            // Remove ActiveAnimationComponent if present
            commandBuffer.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent.getComponentType())

        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to reset player mode: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Spawns a phantom body for the downed player.
     * This body is visible to the downed player only (others see the real player).
     * Allows downed player with weapon to see themselves laying down.
     */
    private fun spawnPhantomBody(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get player's current position (downedLocation might be null initially)
        val playerTransform = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
            ?: return
        val downedLocation = playerTransform.getPosition()

        Log.finer("PlayerMode", "Spawning phantom body at $downedLocation for downed player's view only")

        // Get player's model
        val playerModelComponent = commandBuffer.getComponent(ref, ModelComponent.getComponentType())

        if (playerModelComponent == null || playerModelComponent.model == null) {
            Log.error("PlayerMode", "Player has no model - cannot spawn phantom body")
            return
        }

        // Create phantom body entity
        val holder = EntityStore.REGISTRY.newHolder()

        // Clone model
        val clonedModel = playerModelComponent.clone() as ModelComponent
        holder.addComponent(ModelComponent.getComponentType(), clonedModel)

        // Clone display name
        val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
        if (displayNameComponent != null) {
            val clonedDisplayName = displayNameComponent.clone() as DisplayNameComponent
            holder.addComponent(DisplayNameComponent.getComponentType(), clonedDisplayName)
        }

        // Clone scale
        val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        if (scaleComponent != null) {
            val clonedScale = scaleComponent.clone() as EntityScaleComponent
            holder.addComponent(EntityScaleComponent.getComponentType(), clonedScale)
        }

        // Add Death animation
        val activeAnimation = ActiveAnimationComponent()
        activeAnimation.setPlayingAnimation(AnimationSlot.Movement, "Death")
        holder.addComponent(ActiveAnimationComponent.getComponentType(), activeAnimation)

        // Add sleeping movement states
        val movementStates = MovementStates()
        movementStates.sleeping = true
        movementStates.idle = false
        movementStates.onGround = true
        val movementStatesComponent = MovementStatesComponent()
        movementStatesComponent.movementStates = movementStates
        movementStatesComponent.sentMovementStates = movementStates
        holder.addComponent(MovementStatesComponent.getComponentType(), movementStatesComponent)

        // Set position and rotation
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(downedLocation, playerTransform.getRotation())
        )

        // Add bounding box
        val playerBoundingBox = commandBuffer.getComponent(ref, BoundingBox.getComponentType())
        if (playerBoundingBox != null) {
            val clonedBoundingBox = playerBoundingBox.clone() as BoundingBox
            holder.addComponent(BoundingBox.getComponentType(), clonedBoundingBox)
        } else {
            val defaultBox = Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
            holder.addComponent(BoundingBox.getComponentType(), BoundingBox(defaultBox))
        }

        // Add NetworkId
        val nextNetworkId = commandBuffer.store.externalData.takeNextNetworkId()
        holder.addComponent(NetworkId.getComponentType(), NetworkId(nextNetworkId))

        // Extract player's appearance (equipment and cosmetic skin)
        val (equipment, playerSkin) = extractPlayerAppearance(ref, commandBuffer)

        // CRITICAL: Add PhantomBodyMarker with equipment and skin for deferred processing
        val marker = PhantomBodyMarker(ref, equipment, playerSkin)
        holder.addComponent(PhantomBodyMarker.getComponentType(), marker)

        // Spawn entity (broadcasted to all clients)
        val phantomBodyRef = commandBuffer.addEntity(holder, AddReason.SPAWN)
        component.phantomBodyRef = phantomBodyRef

        // Register phantom body in state tracker so ChannelHandler can identify it
        com.hydowned.network.DownedStateTracker.setPhantomBody(nextNetworkId, ref)

        Log.finer("PlayerMode", "Phantom body spawned (NetworkId: $nextNetworkId) - ChannelHandler will control visibility")

        // Make the REAL player invisible to themselves (so they only see phantom body)
        makePlayerInvisibleToSelf(ref, commandBuffer)
    }

    /**
     * Extracts player's equipment and cosmetic skin for phantom body display.
     * Returns a pair of (Equipment, PlayerSkin).
     */
    private fun extractPlayerAppearance(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ): Pair<Equipment?, com.hypixel.hytale.protocol.PlayerSkin?> {
        var equipment: Equipment? = null
        var playerSkin: com.hypixel.hytale.protocol.PlayerSkin? = null

        // Extract equipment
        val playerComponent = commandBuffer.getComponent(ref, Player.getComponentType())
        if (playerComponent != null) {
            try {
                val inventory = playerComponent.inventory
                equipment = Equipment()

                // Extract armor (4 slots)
                val armor = inventory.armor
                equipment.armorIds = Array(armor.capacity.toInt()) { "" }
                java.util.Arrays.fill(equipment.armorIds, "")

                armor.forEachWithMeta({ slot, itemStack, armorIds ->
                    armorIds[slot.toInt()] = itemStack.itemId
                }, equipment.armorIds)

                // Extract hand items
                val itemInHand = inventory.itemInHand
                equipment.rightHandItemId = itemInHand?.itemId ?: "Empty"

                val utilityItem = inventory.utilityItem
                equipment.leftHandItemId = utilityItem?.itemId ?: "Empty"

                Log.finer("PlayerMode", "Extracted equipment - Right: ${equipment.rightHandItemId}, Left: ${equipment.leftHandItemId}")
            } catch (e: Exception) {
                Log.warning("PlayerMode", "Failed to extract equipment: ${e.message}")
            }
        }

        // Extract player's cosmetic skin (outfit)
        val playerSkinComponent = commandBuffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent.getComponentType())
        if (playerSkinComponent != null) {
            playerSkin = playerSkinComponent.playerSkin
            Log.finer("PlayerMode", "Extracted player cosmetic skin/outfit")
        }

        return Pair(equipment, playerSkin)
    }

    /**
     * Makes the player invisible to their own client by sending a tiny scale update.
     * Other players will still see the real player normally.
     */
    private fun makePlayerInvisibleToSelf(
        ref: Ref<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        try {
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            val networkId = commandBuffer.getComponent(ref, NetworkId.getComponentType())

            if (playerRef != null && networkId != null) {
                // Send scale update to make player tiny (invisible)
                val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
                val originalScale = scaleComponent?.scale ?: 1.0f

                // Store original scale in DownedComponent for restoration
                val downedComponent = commandBuffer.getComponent(ref, DownedComponent.getComponentType())
                if (downedComponent != null) {
                    downedComponent.originalScale = originalScale
                }

                // Create Model component update with tiny scale
                val componentUpdate = ComponentUpdate()
                componentUpdate.type = ComponentUpdateType.Model
                componentUpdate.entityScale = 0.0001f // Tiny scale = invisible

                val entityUpdate = EntityUpdate()
                entityUpdate.networkId = networkId.id
                entityUpdate.updates = arrayOf(componentUpdate)

                val packet = EntityUpdates(null, arrayOf(entityUpdate))
                playerRef.packetHandler.writeNoCache(packet)

                Log.finer("PlayerMode", "Made player invisible to self (scale 0.0001)")
            }
        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to make player invisible: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Restores player visibility by undoing the tiny scale.
     */
    private fun restorePlayerVisibility(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        try {
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType())
            val networkId = commandBuffer.getComponent(ref, NetworkId.getComponentType())

            if (playerRef != null && networkId != null) {
                val originalScale = component.originalScale

                // Create Model component update to restore normal size
                val componentUpdate = ComponentUpdate()
                componentUpdate.type = ComponentUpdateType.Model
                componentUpdate.entityScale = originalScale

                val entityUpdate = EntityUpdate()
                entityUpdate.networkId = networkId.id
                entityUpdate.updates = arrayOf(componentUpdate)

                val packet = EntityUpdates(null, arrayOf(entityUpdate))
                playerRef.packetHandler.writeNoCache(packet)

                Log.finer("PlayerMode", "Restored player visibility (scale $originalScale)")
            }
        } catch (e: Exception) {
            Log.warning("PlayerMode", "Failed to restore player visibility: ${e.message}")
        }
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

        // Get player reference for logging
        val ref = archetypeChunk.getReferenceTo(index)
        val playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
        //Log.fine("SyncSystem", "tick() called for player: ${playerRef?.username ?: "unknown"}")

        // CRITICAL: Check state tracker - if player is not downed anymore, don't force sleeping
        // This prevents forcing sleeping during revive after state tracker is cleared
        if (!com.hydowned.network.DownedStateTracker.isDowned(ref)) {
            Log.fine("SyncSystem", "Player not in state tracker - skipping")
            return
        }

        // Ensure player stays in sleeping state
        val movementStatesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType())
        if (movementStatesComponent == null) {
            Log.warning("SyncSystem", "No MovementStatesComponent found - this shouldn't happen!")
            return
        }

        val states = movementStatesComponent.movementStates
        val sentStates = movementStatesComponent.sentMovementStates

        // Check if any non-sleeping states are active
        val needsReset = !states.sleeping || states.idle || states.walking || states.running ||
                         states.sprinting || !sentStates.sleeping || sentStates.idle ||
                         sentStates.walking || sentStates.running || sentStates.sprinting

        if (needsReset) {
            Log.finer("SyncSystem", "States need reset - forcing sleeping state (sleeping=${states.sleeping}, idle=${states.idle})")
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
        } else {
            //Log.fine("SyncSystem", "States already correct (sleeping=${states.sleeping})")
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
