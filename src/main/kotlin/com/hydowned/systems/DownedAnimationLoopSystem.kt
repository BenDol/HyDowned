package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.AnimationSlot
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log
import com.hydowned.network.DownedStateTracker

/**
 * CRITICAL: Continuously re-sends Death animation ONLY to downed player's client.
 *
 * The Death animation is NOT looping - it plays once and ends. When it ends,
 * the client's animation system defaults to Idle, making the player appear standing
 * from their own perspective (even though other players see them laying down).
 *
 * This system re-sends the Death animation every 10 ticks (~0.5 seconds) directly
 * to the downed player's client ONLY (not to other players). This ensures the
 * downed player sees themselves laying down.
 *
 * Only runs in PLAYER mode.
 */
class DownedAnimationLoopSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    companion object {
        private const val ANIMATION_RESEND_INTERVAL = 10 // ticks
    }

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        PlayerRef.getComponentType(),
        NetworkId.getComponentType()
    )

    // Per-entity tick tracking
    private val entityTickCounters = mutableMapOf<Int, Int>()

    override fun getQuery(): Query<EntityStore> = query

    /**
     * Clears the tick counter for a specific network ID.
     * Should be called when a player is revived or dies to prevent stale counter accumulation.
     */
    fun clearTickCounter(networkId: Int) {
        entityTickCounters.remove(networkId)
    }

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Early return if not PLAYER mode (no config check needed every tick per entity)
        if (!config.usePlayerMode) {
            return
        }

        // CRITICAL: Check state tracker - if player is not downed anymore, don't send animation
        // This prevents sending animation during revive after state tracker is cleared
        val ref = archetypeChunk.getReferenceTo(index)
        if (!DownedStateTracker.isDowned(ref)) {
            Log.finer("AnimationLoop", "Player no longer downed (state tracker cleared), skipping animation")
            return
        }

        // Get network ID early for tick tracking
        val networkIdComponent = archetypeChunk.getComponent(index, NetworkId.getComponentType())
            ?: return
        val networkId = networkIdComponent.id

        // Per-entity tick tracking
        val currentTicks = entityTickCounters.getOrDefault(networkId, 0) + 1
        if (currentTicks < ANIMATION_RESEND_INTERVAL) {
            entityTickCounters[networkId] = currentTicks
            return
        }
        entityTickCounters[networkId] = 0

        // Get player's packet handler to send directly to their client
        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return
        val packetHandler = playerRefComponent.packetHandler

        // Create PlayAnimation packet directly
        val animationPacket = PlayAnimation()
        animationPacket.entityId = networkId
        animationPacket.slot = AnimationSlot.Movement
        animationPacket.animationId = "Sleep"

        // Send ONLY to this player's client (not to other players)
        packetHandler.write(animationPacket)

        Log.finer("AnimationLoop", "Re-sent Sleep animation to player's client (networkId=$networkId)")
    }
}
