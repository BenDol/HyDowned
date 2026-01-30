package com.hydowned.player.system

import com.hydowned.logging.Log
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import com.hypixel.hytale.server.npc.entities.NPCEntity
import com.hypixel.hytale.server.npc.role.Role
import com.hydowned.manager.Managers

/**
 * Manages downed players in AI target lists.
 *
 * - If AI targeting is disabled: removes downed players from target lists entirely
 * - If AI targeting is enabled: removes downed players only if non-downed players are available
 *   (checks both current targets and nearby players within detection range)
 */
class AITargetCleanSystem(private val managers: Managers) : EntityTickingSystem<EntityStore>() {

    private val query: Query<EntityStore> = Query.and(NPCEntity.getComponentType())

    // Track when we last cleared targets for each NPC to prevent spam
    // Key: Entity identity hashCode, Value: timestamp in milliseconds
    private val lastClearTime = mutableMapOf<Int, Long>()
    private val clearCooldownMs = 5000L // 5 seconds

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Check if AI targeting is enabled for downed players
        val aiDamageEnabled = managers.config.downed.allowedDamage.ai.enabled

        val entity: NPCEntity = archetypeChunk.getComponent(index, NPCEntity.getComponentType()!!) ?: return
        val entityId = System.identityHashCode(entity)

        try {
            val role: Role? = entity.role
            if (role == null) {
                return
            }

            val entityTargets = role.markedEntitySupport.entityTargets
            val downedPlayerIndices = mutableListOf<Int>()

            // Find all downed players in the target list
            for (i in entityTargets.indices) {
                val entityTarget: Ref<EntityStore>? = entityTargets[i]
                if (entityTarget == null) continue

                val targetPlayer = entityTarget.store.getComponent(entityTarget, Player.getComponentType())
                if (targetPlayer != null) {
                    val revivePlayer = managers.playerManager.get(targetPlayer)
                    if (revivePlayer?.asDownable?.isDowned() == true) {
                        downedPlayerIndices.add(i)
                    }
                }
            }

            if (downedPlayerIndices.isEmpty()) {
                return
            }

            if (aiDamageEnabled) {
                // Check if there are any alive non-downed players in the target list
                val hasNonDownedTarget = entityTargets.any { target ->
                    if (target == null) return@any false
                    val player = target.store.getComponent(target, Player.getComponentType())
                    if (player != null) {
                        val modPlayer = managers.playerManager.get(player)
                        // Player must be alive AND not downed
                        modPlayer?.asDownable?.let { it.isAlive() && !it.isDowned() } == true
                    } else {
                        true // Non-player targets count as active
                    }
                }

                if (hasNonDownedTarget) {
                    // There are better targets already in list - always remove downed players
                    downedPlayerIndices.forEach { entityTargets[it] = null }

                    // Throttle logging to prevent spam
                    val currentTime = System.currentTimeMillis()
                    val lastClear = lastClearTime[entityId] ?: 0
                    val timeSinceLastClear = currentTime - lastClear

                    if (timeSinceLastClear >= clearCooldownMs) {
                        Log.debug("AITargetClean",
                            "Non-downed targets in list - removed ${downedPlayerIndices.size} downed players")
                        lastClearTime[entityId] = currentTime
                    }
                } else {
                    // Only downed players in target list - check if non-downed players are nearby
                    val transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType())

                    if (transform != null) {
                        val npcPosition = transform.position
                        val detectionRange = managers.config.downed.aiRetargetRange
                        val nearbyEntities = TargetUtil.getAllEntitiesInSphere(npcPosition, detectionRange, store)

                        // Find first alive non-downed player nearby
                        var nonDownedPlayerRef: Ref<EntityStore>? = null
                        for (nearbyRef in nearbyEntities) {
                            val nearbyPlayer = store.getComponent(nearbyRef, Player.getComponentType())
                            if (nearbyPlayer != null) {
                                val nearbyModPlayer = managers.playerManager.get(nearbyPlayer)
                                // Player must be alive AND not downed
                                if (nearbyModPlayer?.asDownable?.let { it.isAlive() && !it.isDowned() } == true) {
                                    nonDownedPlayerRef = nearbyRef
                                    break
                                }
                            }
                        }

                        if (nonDownedPlayerRef != null) {
                            // Non-downed player nearby - replace downed target with non-downed player
                            // This gives AI a specific target to switch to
                            downedPlayerIndices.forEach { entityTargets[it] = nonDownedPlayerRef }

                            // Throttle logging to prevent spam
                            val currentTime = System.currentTimeMillis()
                            val lastClear = lastClearTime[entityId] ?: 0
                            val timeSinceLastClear = currentTime - lastClear

                            if (timeSinceLastClear >= clearCooldownMs) {
                                Log.debug("AITargetClean",
                                    "Non-downed player nearby - replaced ${downedPlayerIndices.size} downed targets with non-downed player")
                                lastClearTime[entityId] = currentTime
                            }
                        }
                        // No else needed - downed targets remain in list when no better options exist
                    }
                }
            } else {
                // AI damage disabled - remove downed players from target list entirely
                downedPlayerIndices.forEach { entityTargets[it] = null }

                // Throttle logging to prevent spam
                val currentTime = System.currentTimeMillis()
                val lastClear = lastClearTime[entityId] ?: 0
                val timeSinceLastClear = currentTime - lastClear

                if (timeSinceLastClear >= clearCooldownMs) {
                    Log.debug("AITargetClean",
                        "Removed ${downedPlayerIndices.size} downed players from target list (AI damage disabled)")
                    lastClearTime[entityId] = currentTime
                }
            }
        } catch (e: Exception) {
            // Log errors at WARNING level (Role internals might change or be unavailable)
            Log.warning("AITargetClean", "Error processing AI targeting: ${e.message}")
        }
    }

    override fun getQuery(): Query<EntityStore>? {
        return query
    }
}
