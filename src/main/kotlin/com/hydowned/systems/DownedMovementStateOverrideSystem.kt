package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.ComponentUpdate
import com.hypixel.hytale.protocol.ComponentUpdateType
import com.hypixel.hytale.protocol.EntityUpdate
import com.hypixel.hytale.protocol.MovementStates
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log

/**
 * CRITICAL: Constantly sends EntityUpdates with sleeping=true to client EVERY TICK.
 *
 * This is necessary because:
 * 1. The server sends EntityUpdates to other players about movements
 * 2. The CLIENT needs to see THEMSELVES as sleeping (self-view)
 * 3. Without constant updates, the client's view reverts to walking/idle
 *
 * This runs EVERY TICK (not every 10 ticks) to ensure the client always has
 * the sleeping state active, overriding any other movement state changes.
 *
 * Only runs in PLAYER mode.
 */
class DownedMovementStateOverrideSystem(
    private val config: DownedConfig
) : EntityTickingSystem<EntityStore>() {

    companion object {
        // Cached MovementStates object to avoid creating new objects every tick
        private val SLEEPING_MOVEMENT_STATES = MovementStates().apply {
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
            inFluid = false
            swimming = false
            swimJumping = false
            mantling = false
            sliding = false
            mounting = false
            rolling = false
            sitting = false
            gliding = false
            onGround = true
        }
    }

    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        PlayerRef.getComponentType(),
        NetworkId.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // PLAYER mode only - early return
        if (!config.usePlayerMode) {
            return
        }

        val playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType())
            ?: return

        // Get player's network ID
        val networkIdComponent = archetypeChunk.getComponent(index, NetworkId.getComponentType())
            ?: return
        val networkId = networkIdComponent.id

        // Create ComponentUpdate for MovementStates (using cached object)
        val componentUpdate = ComponentUpdate()
        componentUpdate.type = ComponentUpdateType.MovementStates
        componentUpdate.movementStates = SLEEPING_MOVEMENT_STATES

        // Create EntityUpdate for this player
        val entityUpdate = EntityUpdate()
        entityUpdate.networkId = networkId
        entityUpdate.updates = arrayOf(componentUpdate)

        // Create EntityUpdates packet
        val entityUpdatesPacket = EntityUpdates(
            null, // no removed entities
            arrayOf(entityUpdate)
        )

        // Send to the player about THEMSELVES - CRITICAL for self-view
        playerRefComponent.packetHandler.writeNoCache(entityUpdatesPacket)
    }
}
