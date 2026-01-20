package com.hydowned.systems

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.math.shape.Box
import com.hypixel.hytale.math.vector.Vector3f
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.Equipment
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.Inventory
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.components.PhantomBodyMarker
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Creates a phantom body NPC at downed location.
 *
 * When player becomes downed:
 * 1. Spawn an NPC entity at downed location with player's model (cloned)
 * 2. Play death animation on the NPC (stays laying down)
 * 3. Store phantom body reference in DownedComponent
 *
 * When player is revived:
 * 1. Teleport player back to downed location
 * 2. Despawn phantom body NPC
 *
 * This allows other players to see the body lying down while the downed player
 * can move freely within 10 blocks (enforced by DownedRadiusConstraintSystem).
 *
 * NOTE: Equipment/armor display requires complex LivingEntity setup - not currently implemented.
 * The phantom body shows the player's base model (skin, body shape) but not their equipment.
 */
class DownedPhantomBodySystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val query = Query.and(
        Player.getComponentType(),
        TransformComponent.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val downedLocation = component.downedLocation ?: return

        println("[HyDowned] [PhantomBody] Player downed, spawning phantom body at $downedLocation")

        // Get player's model and transform to replicate for the phantom body
        val playerModelComponent = commandBuffer.getComponent(ref, ModelComponent.getComponentType())
        val playerTransform = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
            ?: return

        // DEBUG: Check if player has a model
        if (playerModelComponent == null) {
            Log.error("PhantomBody", "ERROR: Player has no ModelComponent!")
            return
        }

        val playerModel = playerModelComponent.model
        if (playerModel == null) {
            Log.error("PhantomBody", "ERROR: Player ModelComponent has null Model!")
            return
        }

        println("[HyDowned] [PhantomBody] Player model: ${playerModel.modelAssetId}")

        // Create a new entity holder for the phantom body
        val holder = EntityStore.REGISTRY.newHolder()

        // Clone player's model for the phantom body (includes base appearance)
        val clonedModel = playerModelComponent.clone() as ModelComponent
        holder.addComponent(ModelComponent.getComponentType(), clonedModel)
        Log.verbose("PhantomBody", "Added ModelComponent to phantom body")

        // Clone player's display name if present
        val displayNameComponent = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType())
        if (displayNameComponent != null) {
            val clonedDisplayName = displayNameComponent.clone() as DisplayNameComponent
            holder.addComponent(DisplayNameComponent.getComponentType(), clonedDisplayName)
            Log.verbose("PhantomBody", "Added DisplayNameComponent to phantom body")
        }

        // Clone player's scale if present
        val scaleComponent = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType())
        if (scaleComponent != null) {
            val clonedScale = scaleComponent.clone() as EntityScaleComponent
            holder.addComponent(EntityScaleComponent.getComponentType(), clonedScale)
            Log.verbose("PhantomBody", "Added EntityScaleComponent to phantom body")
        }

        // Add ActiveAnimationComponent with "Death" animation permanently set
        val activeAnimation = ActiveAnimationComponent()
        activeAnimation.setPlayingAnimation(AnimationSlot.Movement, "Death")
        holder.addComponent(ActiveAnimationComponent.getComponentType(), activeAnimation)
        Log.verbose("PhantomBody", "Added ActiveAnimationComponent (Death animation) to phantom body")

        // CRITICAL: Add MovementStatesComponent with sleeping=true to keep entity laying down
        val movementStates = MovementStates()
        movementStates.sleeping = true
        movementStates.idle = false
        movementStates.onGround = true
        val movementStatesComponent = MovementStatesComponent()
        movementStatesComponent.setMovementStates(movementStates)
        movementStatesComponent.setSentMovementStates(movementStates)
        holder.addComponent(MovementStatesComponent.getComponentType(), movementStatesComponent)
        Log.verbose("PhantomBody", "Added MovementStatesComponent (sleeping=true) to phantom body")

        // Set phantom body position and rotation
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(downedLocation, playerTransform.getRotation())
        )
        Log.verbose("PhantomBody", "Added TransformComponent to phantom body")

        // Add bounding box (needed for spatial tracking and visibility)
        val playerBoundingBox = commandBuffer.getComponent(ref, BoundingBox.getComponentType())
        if (playerBoundingBox != null) {
            val clonedBoundingBox = playerBoundingBox.clone() as BoundingBox
            holder.addComponent(BoundingBox.getComponentType(), clonedBoundingBox)
            Log.verbose("PhantomBody", "Added BoundingBox to phantom body")
        } else {
            // Create a default bounding box if player doesn't have one
            val defaultBox = Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
            val defaultBoundingBox = BoundingBox(defaultBox)
            holder.addComponent(BoundingBox.getComponentType(), defaultBoundingBox)
            Log.verbose("PhantomBody", "Added default BoundingBox to phantom body")
        }

        // CRITICAL: Add NetworkId so the entity gets networked to clients
        val nextNetworkId = store.externalData.takeNextNetworkId()
        holder.addComponent(NetworkId.getComponentType(), NetworkId(nextNetworkId))
        Log.verbose("PhantomBody", "Added NetworkId: $nextNetworkId")

        // Extract player's equipment for phantom body display (BEFORE spawning entity)
        var equipment: Equipment? = null
        val playerComponent = commandBuffer.getComponent(ref, Player.getComponentType())
        if (playerComponent != null) {
            try {
                val inventory = playerComponent.inventory
                equipment = Equipment()

                println("[HyDowned] [PhantomBody] DEBUG: Inventory class: ${inventory.javaClass.name}")
                println("[HyDowned] [PhantomBody] DEBUG: Active hotbar slot: ${inventory.activeHotbarSlot}")

                // Extract armor (4 slots: head, chest, hands, legs)
                val armor = inventory.armor
                println("[HyDowned] [PhantomBody] DEBUG: Armor container capacity: ${armor.capacity}")
                println("[HyDowned] [PhantomBody] DEBUG: Armor container class: ${armor.javaClass.name}")

                equipment.armorIds = Array(armor.capacity.toInt()) { "" }
                java.util.Arrays.fill(equipment.armorIds, "")

                var armorCount = 0
                armor.forEachWithMeta({ slot, itemStack, armorIds ->
                    println("[HyDowned] [PhantomBody] DEBUG: Armor slot $slot: ${itemStack.itemId}")
                    armorIds[slot.toInt()] = itemStack.itemId
                    armorCount++
                }, equipment.armorIds)
                println("[HyDowned] [PhantomBody] DEBUG: Found $armorCount armor items")

                // Extract hand items
                // Check what getItemInHand() is actually returning
                val activeHotbarItem = inventory.activeHotbarItem
                println("[HyDowned] [PhantomBody] DEBUG: Active hotbar item (slot ${inventory.activeHotbarSlot}): ${activeHotbarItem?.itemId ?: "null"}")

                val itemInHand = inventory.itemInHand
                println("[HyDowned] [PhantomBody] DEBUG: Item in hand (final): ${itemInHand?.itemId ?: "null"}")

                equipment.rightHandItemId = if (itemInHand != null) itemInHand.itemId else "Empty"

                val utilityItem = inventory.utilityItem
                println("[HyDowned] [PhantomBody] DEBUG: Utility item: ${utilityItem?.itemId ?: "null"}")
                equipment.leftHandItemId = if (utilityItem != null) utilityItem.itemId else "Empty"

                // DEBUG: Also try to iterate all inventory slots
                println("[HyDowned] [PhantomBody] DEBUG: Hotbar capacity: ${inventory.hotbar.capacity}")
                var hotbarCount = 0

                // Check all 9 hotbar slots
                for (i in 0 until inventory.hotbar.capacity.toInt()) {
                    val item = inventory.hotbar.getItemStack(i.toShort())
                    if (item != null) {
                        println("[HyDowned] [PhantomBody] DEBUG: Hotbar slot $i: ${item.itemId}")
                        hotbarCount++
                    } else {
                        println("[HyDowned] [PhantomBody] DEBUG: Hotbar slot $i: empty")
                    }
                }
                println("[HyDowned] [PhantomBody] DEBUG: Found $hotbarCount hotbar items total")

                // Store equipment data in component for later use
                component.equipmentData = equipment

                Log.verbose("PhantomBody", "Extracted player equipment for phantom body")
                println("[HyDowned] [PhantomBody]   - Armor: ${equipment.armorIds?.joinToString(", ")}")
                println("[HyDowned] [PhantomBody]   - Right hand: ${equipment.rightHandItemId}")
                println("[HyDowned] [PhantomBody]   - Left hand: ${equipment.leftHandItemId}")

                // Check if player has any equipment at all
                val hasArmor = equipment.armorIds?.any { it.isNotEmpty() } == true
                val hasRightHand = equipment.rightHandItemId != null && equipment.rightHandItemId != "Empty"
                val hasLeftHand = equipment.leftHandItemId != null && equipment.leftHandItemId != "Empty"

                if (!hasArmor && !hasRightHand && !hasLeftHand) {
                    Log.warning("PhantomBody", "Player has NO equipment/armor equipped!")
                    Log.warning("PhantomBody", "To see equipment on phantom body:")
                    println("[HyDowned] [PhantomBody]   1. Equip armor (helmet, chestplate, etc.)")
                    println("[HyDowned] [PhantomBody]   2. Select a hotbar slot (press 1-9) to hold an item")
                    println("[HyDowned] [PhantomBody]   Active hotbar slot was: ${inventory.activeHotbarSlot} (needs to be 0-8)")
                }
            } catch (e: Exception) {
                Log.warning("PhantomBody", "Failed to extract equipment: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.warning("PhantomBody", "Player component is null!")
        }

        // Extract player's cosmetic skin (outfit)
        var playerSkin: com.hypixel.hytale.protocol.PlayerSkin? = null
        val playerSkinComponent = commandBuffer.getComponent(ref, PlayerSkinComponent.getComponentType())
        if (playerSkinComponent != null) {
            playerSkin = playerSkinComponent.playerSkin
            Log.verbose("PhantomBody", "Extracted player cosmetic skin/outfit")
        } else {
            Log.warning("PhantomBody", "No PlayerSkinComponent found (no cosmetic outfit)")
        }

        // Add PhantomBodyMarker with player reference, equipment, and cosmetic skin for deferred processing
        val marker = PhantomBodyMarker(ref, equipment, playerSkin)
        holder.addComponent(PhantomBodyMarker.getComponentType(), marker)
        Log.verbose("PhantomBody", "Added PhantomBodyMarker with equipment and skin data")

        // Add the phantom body entity to the world
        val phantomBodyRef = commandBuffer.addEntity(holder, AddReason.SPAWN)
        Log.verbose("PhantomBody", "Added entity to world with ref: $phantomBodyRef")

        // Store phantom body reference
        component.phantomBodyRef = phantomBodyRef

        Log.verbose("PhantomBody", "Created phantom body entity")
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
        println("[HyDowned] [PhantomBody] DownedComponent removed - cleaning up")

        // Check if player entity is still valid (not being removed/logged out)
        val isPlayerValid = ref.isValid

        // Only teleport player back if they're still in the world (revived, not logged out)
        if (isPlayerValid) {
            println("[HyDowned] [PhantomBody] Player still in world - teleporting back to body")

            // CRITICAL: Teleport player back to downed location (where phantom body is)
            val downedLocation = component.downedLocation
            if (downedLocation != null) {
                val transformComponent = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
                if (transformComponent != null) {
                    // Use Teleport component for proper client-side teleportation
                    val currentRotation = transformComponent.getRotation()
                    val teleport = Teleport.createForPlayer(downedLocation, currentRotation)
                    commandBuffer.addComponent(ref, Teleport.getComponentType(), teleport)
                    Log.verbose("PhantomBody", "Teleporting player back to $downedLocation")
                }
            }
        } else {
            println("[HyDowned] [PhantomBody] Player entity being removed (logout) - skipping teleport")
        }

        // Always remove phantom body entity
        val phantomBodyRef = component.phantomBodyRef
        if (phantomBodyRef != null && phantomBodyRef.isValid) {
            commandBuffer.removeEntity(phantomBodyRef, RemoveReason.UNLOAD)
            Log.verbose("PhantomBody", "Removed phantom body entity")
        }
    }
}
