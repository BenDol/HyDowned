package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.ComponentUpdate
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.PhantomBodyMarker
import com.hydowned.config.DownedConfig

/**
 * System that plays death animation and sends equipment updates on phantom bodies after they're fully spawned.
 *
 * When a phantom body is created, it has a PhantomBodyMarker component with player reference and equipment data.
 * This system checks if the phantom body has a NetworkId and Visible component (meaning it's fully spawned and networked),
 * plays the death animation, sends equipment updates to all viewers, and removes the marker.
 */
class PhantomBodyAnimationSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    private val query = Query.and(
        PhantomBodyMarker.getComponentType(),
        ModelComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val ref = archetypeChunk.getReferenceTo(index)

        println("[HyDowned] [PhantomAnimation] Checking phantom body entity...")

        // Check if entity is fully spawned (has NetworkId)
        val networkId = commandBuffer.getComponent(ref, NetworkId.getComponentType())
        if (networkId != null) {
            println("[HyDowned] [PhantomAnimation] ✓ Entity has NetworkId: ${networkId.id}")

            // Check if entity is visible to clients (has Visible component)
            val visible = store.getComponent(ref, EntityTrackerSystems.Visible.getComponentType())
            if (visible != null && visible.visibleTo.isNotEmpty()) {
                println("[HyDowned] [PhantomAnimation] ✓ Entity is visible to ${visible.visibleTo.size} viewers")

                // Get the marker component to access equipment data
                val marker = commandBuffer.getComponent(ref, PhantomBodyMarker.getComponentType())

                // Play death animation
                try {
                    AnimationUtils.playAnimation(
                        ref,
                        AnimationSlot.Movement,
                        "Death",
                        false,
                        commandBuffer
                    )
                    println("[HyDowned] [PhantomAnimation] ✓ Played death animation on phantom body")
                } catch (e: Exception) {
                    println("[HyDowned] [PhantomAnimation] ✗ Failed to play animation: ${e.message}")
                    e.printStackTrace()
                }

                // Send equipment update if we have equipment data
                val equipmentData = marker?.equipment
                if (equipmentData != null) {
                    try {
                        val update = ComponentUpdate()
                        update.type = ComponentUpdateType.Equipment
                        update.equipment = equipmentData

                        // Queue equipment update to all viewers
                        for ((viewerRef, viewer) in visible.visibleTo) {
                            try {
                                viewer.queueUpdate(ref, update)
                            } catch (e: Exception) {
                                println("[HyDowned] [PhantomAnimation] ✗ Failed to queue equipment update for viewer: ${e.message}")
                            }
                        }

                        println("[HyDowned] [PhantomAnimation] ✓ Queued equipment update to ${visible.visibleTo.size} viewers")
                        println("[HyDowned] [PhantomAnimation]   - Armor: ${equipmentData.armorIds?.joinToString(", ")}")
                        println("[HyDowned] [PhantomAnimation]   - Right hand: ${equipmentData.rightHandItemId}")
                        println("[HyDowned] [PhantomAnimation]   - Left hand: ${equipmentData.leftHandItemId}")
                    } catch (e: Exception) {
                        println("[HyDowned] [PhantomAnimation] ✗ Failed to send equipment update: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("[HyDowned] [PhantomAnimation] ⚠ No equipment data in marker")
                }

                // Send cosmetic skin update if we have skin data
                val playerSkinData = marker?.playerSkin
                if (playerSkinData != null) {
                    try {
                        val skinUpdate = ComponentUpdate()
                        skinUpdate.type = ComponentUpdateType.PlayerSkin
                        skinUpdate.skin = playerSkinData

                        // Queue skin update to all viewers
                        for ((viewerRef, viewer) in visible.visibleTo) {
                            try {
                                viewer.queueUpdate(ref, skinUpdate)
                            } catch (e: Exception) {
                                println("[HyDowned] [PhantomAnimation] ✗ Failed to queue skin update for viewer: ${e.message}")
                            }
                        }

                        println("[HyDowned] [PhantomAnimation] ✓ Queued cosmetic skin/outfit update to ${visible.visibleTo.size} viewers")
                    } catch (e: Exception) {
                        println("[HyDowned] [PhantomAnimation] ✗ Failed to send skin update: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("[HyDowned] [PhantomAnimation] ⚠ No cosmetic skin data in marker")
                }

                // Remove the marker component so we don't process again
                commandBuffer.removeComponent(ref, PhantomBodyMarker.getComponentType())
            } else {
                println("[HyDowned] [PhantomAnimation] ✗ Entity not visible yet, waiting...")
            }
        } else {
            println("[HyDowned] [PhantomAnimation] ✗ Entity does NOT have NetworkId yet, waiting...")
        }
    }
}
