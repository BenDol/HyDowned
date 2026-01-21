package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.protocol.AccumulationMode
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect
import com.hypixel.hytale.protocol.packets.world.UpdatePostFxSettings
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log

/**
 * Applies visual screen effects to downed players.
 *
 * When a player becomes downed:
 * - Applies subtle camera shake for disorientation
 * - Reduces post-processing effects (darker/desaturated vision)
 *
 * When player is revived or dies:
 * - Removes all screen effects and restores normal vision
 */
class DownedScreenEffectsSystem(
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
        Log.verbose("ScreenEffects", "============================================")
        Log.verbose("ScreenEffects", "Applying disorientation effects to downed player")

        try {
            // Get PlayerRef to send packets
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType()) ?: run {
                Log.warning("ScreenEffects", "Could not get PlayerRef component")
                return
            }

            // Apply subtle camera shake for disorientation
            try {
                val cameraShake = CameraShakeEffect(
                    0,      // cameraShakeId (0 = generic shake)
                    0.15f,  // intensity (subtle shake)
                    AccumulationMode.Set
                )
                playerRef.packetHandler.writeNoCache(cameraShake as Packet)
                Log.verbose("ScreenEffects", "Applied camera shake (intensity: 0.15)")
            } catch (e: Exception) {
                Log.warning("ScreenEffects", "Failed to apply camera shake: ${e.message}")
                e.printStackTrace()
            }

            // Reduce post-processing effects (darker, less vibrant vision)
            try {
                val postFx = UpdatePostFxSettings(
                    0.5f,  // globalIntensity - reduced brightness
                    0.8f,  // power - affects bloom/glow
                    0.0f,  // sunshaftScale - no god rays
                    0.5f,  // sunIntensity - dimmed sun
                    0.0f   // sunshaftIntensity - no sun shafts
                )
                playerRef.packetHandler.writeNoCache(postFx as Packet)
                Log.verbose("ScreenEffects", "Applied post-FX (reduced brightness/intensity)")
            } catch (e: Exception) {
                Log.warning("ScreenEffects", "Failed to apply post-FX: ${e.message}")
                e.printStackTrace()
            }

        } catch (e: Exception) {
            Log.warning("ScreenEffects", "Failed to apply screen effects: ${e.message}")
            e.printStackTrace()
        }

        Log.verbose("ScreenEffects", "============================================")
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
        Log.verbose("ScreenEffects", "============================================")
        Log.verbose("ScreenEffects", "Removing disorientation effects from player")

        try {
            // Get PlayerRef to send packets
            val playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType()) ?: run {
                Log.warning("ScreenEffects", "Could not get PlayerRef component")
                return
            }

            // Remove camera shake
            try {
                val cameraShake = CameraShakeEffect(
                    0,      // cameraShakeId
                    0.0f,   // intensity (no shake)
                    AccumulationMode.Set
                )
                playerRef.packetHandler.writeNoCache(cameraShake as Packet)
                Log.verbose("ScreenEffects", "Removed camera shake")
            } catch (e: Exception) {
                Log.warning("ScreenEffects", "Failed to remove camera shake: ${e.message}")
                e.printStackTrace()
            }

            // Reset post-processing effects to default
            try {
                val postFx = UpdatePostFxSettings(
                    1.0f,   // globalIntensity - normal brightness
                    1.0f,          // power - normal bloom/glow
                    1.0f,   // sunshaftScale - normal god rays
                    1.0f,     // sunIntensity - normal sun
                    1.0f  // sunshaftIntensity - normal sun shafts
                )
                playerRef.packetHandler.writeNoCache(postFx as Packet)
                Log.verbose("ScreenEffects", "Reset post-FX to normal")
            } catch (e: Exception) {
                Log.warning("ScreenEffects", "Failed to reset post-FX: ${e.message}")
                e.printStackTrace()
            }

        } catch (e: Exception) {
            Log.warning("ScreenEffects", "Failed to remove screen effects: ${e.message}")
            e.printStackTrace()
        }

        Log.verbose("ScreenEffects", "============================================")
    }
}
