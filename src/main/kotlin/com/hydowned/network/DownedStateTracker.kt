package com.hydowned.network

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe tracker for downed player state and network IDs.
 *
 * This allows network threads (packet handlers) to query if a player is downed
 * and get their network ID without accessing the ECS Store (which requires world thread).
 *
 * Updated from world thread by systems when players become downed/revived.
 */
object DownedStateTracker {
    private val downedPlayers = ConcurrentHashMap.newKeySet<Ref<EntityStore>>()
    private val networkIds = ConcurrentHashMap<Ref<EntityStore>, Int>()

    /**
     * Marks a player as downed. Called from world thread.
     */
    fun setDowned(playerRef: Ref<EntityStore>) {
        downedPlayers.add(playerRef)
        println("[HyDowned] StateTracker: Player marked as downed")
    }

    /**
     * Marks a player as not downed. Called from world thread.
     */
    fun setNotDowned(playerRef: Ref<EntityStore>) {
        downedPlayers.remove(playerRef)
        println("[HyDowned] StateTracker: Player marked as not downed")
    }

    /**
     * Checks if a player is downed. Thread-safe, can be called from any thread.
     */
    fun isDowned(playerRef: Ref<EntityStore>): Boolean {
        return downedPlayers.contains(playerRef)
    }

    /**
     * Sets the network ID for a player. Called from world thread.
     */
    fun setNetworkId(playerRef: Ref<EntityStore>, networkId: Int) {
        networkIds[playerRef] = networkId
    }

    /**
     * Gets the network ID for a player. Thread-safe, can be called from any thread.
     * Returns null if not tracked.
     */
    fun getNetworkId(playerRef: Ref<EntityStore>): Int? {
        return networkIds[playerRef]
    }

    /**
     * Removes a player from tracking (called when player disconnects)
     */
    fun remove(playerRef: Ref<EntityStore>) {
        downedPlayers.remove(playerRef)
        networkIds.remove(playerRef)
    }
}
