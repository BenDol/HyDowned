package com.hydowned.util

/**
 * Simple logging system with configurable log levels
 *
 * Log Levels:
 * - ERROR: Critical errors that need immediate attention
 * - WARNING: Potential issues or unexpected behavior
 * - INFO: Important informational messages
 * - VERBOSE: Detailed operational messages
 * - DEBUG: Very detailed debugging information
 *
 * Usage:
 * ```
 * Logger.info("System", "Player entered downed state")
 * Logger.debug("System", "Current health: $health")
 * Logger.error("System", "Failed to save data: ${e.message}")
 * ```
 */
object Log {

    enum class LogLevel(val priority: Int) {
        ERROR(0),
        WARNING(1),
        INFO(2),
        VERBOSE(3),
        DEBUG(4)
    }

    private var enabledLevel: LogLevel = LogLevel.INFO

    /**
     * Set the minimum log level to display
     * All logs at this level or higher priority will be shown
     */
    fun setLogLevel(level: LogLevel) {
        enabledLevel = level
        println("[HyDowned] Log level set to: $level")
    }

    /**
     * Set log level from string (for config loading)
     */
    fun setLogLevel(level: String) {
        try {
            val parsedLevel = LogLevel.valueOf(level.uppercase())
            setLogLevel(parsedLevel)
        } catch (e: IllegalArgumentException) {
            println("[HyDowned] [Logger] Invalid log level: $level, using INFO")
            setLogLevel(LogLevel.INFO)
        }
    }

    /**
     * Check if a log level is enabled
     */
    fun isEnabled(level: LogLevel): Boolean {
        return level.priority <= enabledLevel.priority
    }

    /**
     * Log an error message (always shown)
     */
    fun error(category: String, message: String) {
        if (isEnabled(LogLevel.ERROR)) {
            println("[HyDowned] [ERROR] [$category] $message")
        }
    }

    /**
     * Log a warning message
     */
    fun warning(category: String, message: String) {
        if (isEnabled(LogLevel.WARNING)) {
            println("[HyDowned] [WARNING] [$category] $message")
        }
    }

    /**
     * Log an info message
     */
    fun info(category: String, message: String) {
        if (isEnabled(LogLevel.INFO)) {
            println("[HyDowned] [INFO] [$category] $message")
        }
    }

    /**
     * Log a verbose message (detailed operations)
     */
    fun verbose(category: String, message: String) {
        if (isEnabled(LogLevel.VERBOSE)) {
            println("[HyDowned] [VERBOSE] [$category] $message")
        }
    }

    /**
     * Log a debug message (very detailed)
     */
    fun debug(category: String, message: String) {
        if (isEnabled(LogLevel.DEBUG)) {
            println("[HyDowned] [DEBUG] [$category] $message")
        }
    }

    /**
     * Log a separator line (only for VERBOSE and DEBUG)
     */
    fun separator(category: String) {
        if (isEnabled(LogLevel.VERBOSE)) {
            println("[HyDowned] [$category] ============================================")
        }
    }
}
