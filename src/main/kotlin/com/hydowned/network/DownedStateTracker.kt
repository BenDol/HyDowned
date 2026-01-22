package com.hydowned.network

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.ConcurrentHashMap
import com.hydowned.util.Log


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
    // Maps phantom body network ID -> player ref who owns it
    private val phantomBodies = ConcurrentHashMap<Int, Ref<EntityStore>>()

    /**
     * Marks a player as downed. Called from world thread.
     */
    fun setDowned(playerRef: Ref<EntityStore>) {
        downedPlayers.add(playerRef)
        Log.finer("StateTracker", "StateTracker: Player marked as downed")
    }

    /**
     * Marks a player as not downed. Called from world thread.
     */
    fun setNotDowned(playerRef: Ref<EntityStore>) {
        downedPlayers.remove(playerRef)
        Log.finer("StateTracker", "StateTracker: Player marked as not downed")
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
     * Registers a phantom body for a player. Called from world thread.
     */
    fun setPhantomBody(phantomNetworkId: Int, ownerPlayerRef: Ref<EntityStore>) {
        phantomBodies[phantomNetworkId] = ownerPlayerRef
        Log.finer("StateTracker", "StateTracker: Phantom body $phantomNetworkId registered for player")
    }

    /**
     * Gets the owner of a phantom body. Thread-safe, can be called from any thread.
     * Returns the player ref who owns this phantom body, or null if not a phantom body.
     */
    fun getPhantomBodyOwner(phantomNetworkId: Int): Ref<EntityStore>? {
        return phantomBodies[phantomNetworkId]
    }

    /**
     * Checks if a network ID is a phantom body. Thread-safe, can be called from any thread.
     */
    fun isPhantomBody(networkId: Int): Boolean {
        return phantomBodies.containsKey(networkId)
    }

    /**
     * Removes phantom body tracking. Called from world thread.
     */
    fun removePhantomBody(phantomNetworkId: Int) {
        phantomBodies.remove(phantomNetworkId)
        Log.finer("StateTracker", "StateTracker: Phantom body $phantomNetworkId unregistered")
    }

    /**
     * Removes a player from tracking (called when player disconnects)
     */
    fun remove(playerRef: Ref<EntityStore>) {
        downedPlayers.remove(playerRef)
        networkIds.remove(playerRef)
        // Also remove any phantom bodies owned by this player
        phantomBodies.values.removeIf { it == playerRef }
    }
}
