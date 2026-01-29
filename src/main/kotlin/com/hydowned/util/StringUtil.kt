package com.hydowned.util

/**
 * Utility functions.
 */
object StringUtil {
    /**
     * Formats time in seconds to a human-readable string.
     *
     * @param seconds Time in seconds
     * @return Formatted time string (e.g., "1:30", "45s")
     */
    fun formatTime(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60

        return if (minutes > 0) {
            String.format("%d:%02d", minutes, secs)
        } else {
            String.format("%ds", secs)
        }
    }
}
