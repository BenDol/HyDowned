package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * System-based approach to revive interactions
 *
 * This system runs every 0.5 seconds on downed players.
 * For each downed player, it checks if any alive players are within revive range AND crouching.
 *
 * Revive starts automatically when:
 * - Player is within revive range (default 3 blocks)
 * - Player is crouching
 *
 * Revive cancels when:
 * - Player stops crouching
 * - Player moves out of range
 *
 * Alternative to event-based approach which isn't working.
 */
class ReviveInteractionSystem(
    private val config: DownedConfig
) : DelayedEntitySystem<EntityStore>(0.5f) { // Run twice per second

    // Query for downed players
    private val query = Query.and(
        Player.getComponentType(),
        DownedComponent.getComponentType(),
        TransformComponent.getComponentType()
    )

    override fun getQuery(): Query<EntityStore> = query

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // This is a downed player - check if any alive players are nearby
        val downedRef = archetypeChunk.getReferenceTo(index)
        val downedPlayer = archetypeChunk.getComponent(index, Player.getComponentType())
            ?: return
        val downedUuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType())
            ?: return
        val downedComponent = archetypeChunk.getComponent(index, DownedComponent.getComponentType())
            ?: return

        // CRITICAL: Use phantom body position (downed location), NOT the player's current position
        // The player can move within 10 blocks, but revival happens at the body location
        val downedPos = downedComponent.downedLocation ?: return

        // Get all players from Universe (high-level API)
        val allPlayers = Universe.get().players
        val nearbyAlivePlayerUUIDs = mutableSetOf<String>()

        // Check each player to see if they're alive and nearby
        for (alivePlayerRef in allPlayers) {
            val aliveEntityRef = alivePlayerRef.reference ?: continue

            // Skip the downed player itself
            if (aliveEntityRef == downedRef) {
                continue
            }

            // Check if this player is downed - if so, skip
            val isPlayerDowned = store.getComponent(aliveEntityRef, DownedComponent.getComponentType()) != null
            if (isPlayerDowned) {
                continue
            }

            // Get alive player's transform
            val aliveTransform = store.getComponent(aliveEntityRef, TransformComponent.getComponentType())
                ?: continue
            val alivePos = aliveTransform.position

            // Check if player is crouching
            val movementStates = store.getComponent(aliveEntityRef, MovementStatesComponent.getComponentType())
            val isCrouching = movementStates?.movementStates?.crouching ?: false

            // Calculate distance
            val dx = alivePos.x - downedPos.x
            val dy = alivePos.y - downedPos.y
            val dz = alivePos.z - downedPos.z
            val distanceSquared = dx * dx + dy * dy + dz * dz
            val reviveRangeSquared = config.reviveRange * config.reviveRange

            // Player is within revive range AND crouching
            if (distanceSquared <= reviveRangeSquared && isCrouching) {
                val reviverUUID = alivePlayerRef.uuid.toString()
                nearbyAlivePlayerUUIDs.add(reviverUUID)

                // Check if this reviver is already reviving
                if (!downedComponent.reviverPlayerIds.contains(reviverUUID)) {
                    val downedPlayerName = downedPlayer.displayName ?: "Player"

                    // Add reviver
                    downedComponent.reviverPlayerIds.add(reviverUUID)

                    // Initialize revive timer if this is the first reviver
                    if (downedComponent.reviverPlayerIds.size == 1) {
                        downedComponent.reviveTimeRemaining = config.reviveTimerSeconds.toDouble()
                        // PERFORMANCE: Commented out (runs every 0.5s)
                        // Log.verbose("ReviveInteraction", "Initialized revive timer: ${config.reviveTimerSeconds}s")
                    }

                    // Send feedback
                    alivePlayerRef.sendMessage(Message.raw("Reviving $downedPlayerName - stay crouched"))
                    downedPlayer.sendMessage(Message.raw("${alivePlayerRef.username} is reviving you"))
                }
            }
        }

        // Remove revivers that are no longer in range OR not crouching
        val iterator = downedComponent.reviverPlayerIds.iterator()
        while (iterator.hasNext()) {
            val reviverUUID = iterator.next()
            if (!nearbyAlivePlayerUUIDs.contains(reviverUUID)) {
                iterator.remove()
                // PERFORMANCE: Commented out (runs every 0.5s)
                // Log.verbose("ReviveInteraction", "Reviver stopped reviving (not crouching or out of range): $reviverUUID")

                // Try to send message to the reviver if they're still online
                for (player in allPlayers) {
                    if (player.uuid.toString() == reviverUUID) {
                        player.sendMessage(Message.raw("Revive cancelled"))
                        break
                    }
                }

                // Reset timer if no revivers left
                if (downedComponent.reviverPlayerIds.isEmpty()) {
                    downedComponent.reviveTimeRemaining = 0.0
                }
            }
        }
    }
}
