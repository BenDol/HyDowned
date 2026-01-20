package com.hydowned.util

import com.hypixel.hytale.math.vector.Vector3d
import java.io.File
import java.util.UUID
import com.hydowned.util.Log


/**
 * Tracks players who should die OR restore downed state when they log back in.
 *
 * Uses a file-based approach since DownedComponent doesn't serialize.
 * File format: plugins/HyDowned/pending-deaths/<uuid>.txt
 * File contents:
 * - "DEATH" = execute death on rejoin (intentional logout)
 * - "RESTORE:<seconds>" = restore downed state with timer (crash/unload)
 *
 * This persists across server restarts.
 */
object PendingDeathTracker {
    private val pendingDeathsDir = File("plugins/HyDowned/pending-deaths")

    init {
        // Ensure directory exists
        pendingDeathsDir.mkdirs()
        println("[HyDowned] [DeathTracker] Initialized with directory: ${pendingDeathsDir.absolutePath}")
    }

    /**
     * Mark a player for death on next login (intentional logout)
     */
    fun markForDeath(playerUuid: UUID) {
        val markerFile = File(pendingDeathsDir, "$playerUuid.txt")
        try {
            markerFile.writeText("DEATH")
            println("[HyDowned] [DeathTracker] Marked $playerUuid for death on next login")
        } catch (e: Exception) {
            Log.warning("DeathTracker", "Failed to create death marker for $playerUuid: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Mark a player to restore downed state on next login (crash/unload)
     * @param timeRemaining Seconds remaining on downed timer
     * @param downedLocation Location where player was downed (null if unknown)
     */
    fun markForRestore(playerUuid: UUID, timeRemaining: Int, downedLocation: Vector3d?) {
        val markerFile = File(pendingDeathsDir, "$playerUuid.txt")
        try {
            val locationStr = if (downedLocation != null) {
                ":${downedLocation.x}:${downedLocation.y}:${downedLocation.z}"
            } else {
                ""
            }
            markerFile.writeText("RESTORE:$timeRemaining$locationStr")
            println("[HyDowned] [DeathTracker] Marked $playerUuid to restore downed state ($timeRemaining seconds, location: $downedLocation) on next login")
        } catch (e: Exception) {
            Log.warning("DeathTracker", "Failed to create restore marker for $playerUuid: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Check what should happen to the player on login
     * @return RestoreAction indicating what to do
     */
    fun checkAndClearAction(playerUuid: UUID): RestoreAction {
        val markerFile = File(pendingDeathsDir, "$playerUuid.txt")

        if (!markerFile.exists()) {
            return RestoreAction.None
        }

        try {
            val content = markerFile.readText().trim()
            markerFile.delete() // Clear marker after reading

            return when {
                content == "DEATH" -> {
                    println("[HyDowned] [DeathTracker] Player $playerUuid should die on login")
                    RestoreAction.ExecuteDeath
                }
                content.startsWith("RESTORE:") -> {
                    val parts = content.substringAfter("RESTORE:").split(":")
                    val timeRemaining = parts.getOrNull(0)?.toIntOrNull() ?: 0

                    // Parse location if present (format: RESTORE:seconds:x:y:z)
                    val location = if (parts.size >= 4) {
                        val x = parts[1].toDoubleOrNull()
                        val y = parts[2].toDoubleOrNull()
                        val z = parts[3].toDoubleOrNull()
                        if (x != null && y != null && z != null) {
                            Vector3d(x, y, z)
                        } else null
                    } else null

                    println("[HyDowned] [DeathTracker] Player $playerUuid should restore downed state ($timeRemaining seconds, location: $location)")
                    RestoreAction.RestoreDowned(timeRemaining, location)
                }
                else -> {
                    Log.warning("DeathTracker", "Unknown marker content: $content")
                    RestoreAction.None
                }
            }
        } catch (e: Exception) {
            Log.warning("DeathTracker", "Error reading marker file for $playerUuid: ${e.message}")
            e.printStackTrace()
            // Clean up corrupted file
            markerFile.delete()
            return RestoreAction.None
        }
    }

    /**
     * Legacy compatibility - check if player should die on login
     * @deprecated Use checkAndClearAction instead
     */
    @Deprecated("Use checkAndClearAction instead", ReplaceWith("checkAndClearAction(playerUuid) == RestoreAction.ExecuteDeath"))
    fun checkAndClearDeathPending(playerUuid: UUID): Boolean {
        return checkAndClearAction(playerUuid) == RestoreAction.ExecuteDeath
    }

    /**
     * Get count of pending actions (for debugging)
     */
    fun getPendingCount(): Int {
        return pendingDeathsDir.listFiles()?.size ?: 0
    }

    /**
     * Represents what should happen to a player on login
     */
    sealed class RestoreAction {
        /** No action needed - normal login */
        object None : RestoreAction()
        /** Execute death and respawn */
        object ExecuteDeath : RestoreAction()
        /** Restore downed state with given time remaining and location */
        data class RestoreDowned(val timeRemaining: Int, val downedLocation: Vector3d?) : RestoreAction()
    }
}
