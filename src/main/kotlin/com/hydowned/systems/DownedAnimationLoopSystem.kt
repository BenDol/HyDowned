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

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        PlayerRef.getComponentType(),
        NetworkId.getComponentType()
    )

    private var tickCounter = 0

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only
        if (!config.usePlayerMode) {
            return
        }

        // Re-send animation every 10 ticks (~0.5 seconds) to keep it looping
        tickCounter++
        if (tickCounter < 10) {
            return
        }
        tickCounter = 0

        // Get player's network ID
        val networkIdComponent = archetypeChunk.getComponent(index, NetworkId.getComponentType())
            ?: return
        val networkId = networkIdComponent.id

        // Get player's packet handler to send directly to their client
        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return
        val packetHandler = playerRefComponent.packetHandler

        // Create PlayAnimation packet directly
        val animationPacket = PlayAnimation()
        animationPacket.entityId = networkId
        animationPacket.slot = AnimationSlot.Movement
        animationPacket.animationId = "Death"

        // Send ONLY to this player's client (not to other players)
        packetHandler.write(animationPacket)

        Log.verbose("AnimationLoop", "Re-sent Death animation directly to downed player's client only")
    }
}
