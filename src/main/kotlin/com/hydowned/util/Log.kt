package com.hydowned.util

import com.hypixel.hytale.logger.HytaleLogger

/**
 * Logging system for HyDowned mod.
 *
 * Outputs to stdout/stderr which is captured by Hytale's logging system.
 * The server's log level configuration will control what actually appears in logs.
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
 * Log.info("System", "Player entered downed state")
 * Log.debug("System", "Current health: $health")
 * Log.error("System", "Failed to save data: ${e.message}")
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

    private var logger: HytaleLogger? = null
    private var enabledLevel: LogLevel = LogLevel.INFO

    /**
     * Initialize with Hytale logger (should be called from plugin setup)
     */
    fun init(pluginLogger: HytaleLogger) {
        logger = pluginLogger
        // Log initialization using the logger
        logger?.atInfo()?.log("HyDowned logging system initialized")
    }

    /**
     * Set the minimum log level to display.
     * All logs at this level or higher priority will be shown.
     * Note: The server's log level also applies - this is an additional filter.
     */
    fun setLogLevel(level: LogLevel) {
        enabledLevel = level
        logger?.atInfo()?.log("Log level set to: $level")
    }

    /**
     * Set log level from string (for config loading)
     */
    fun setLogLevel(level: String) {
        try {
            val parsedLevel = LogLevel.valueOf(level.uppercase())
            setLogLevel(parsedLevel)
        } catch (e: IllegalArgumentException) {
            logger?.atInfo()?.log("Invalid log level: $level, using INFO")
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
     * Log an error message (always shown if server ERROR level is enabled)
     */
    fun error(category: String, message: String) {
        if (isEnabled(LogLevel.ERROR)) {
            logger?.atSevere()?.log("[ERROR] [$category] $message")
        }
    }

    /**
     * Log a warning message
     */
    fun warning(category: String, message: String) {
        if (isEnabled(LogLevel.WARNING)) {
            logger?.atWarning()?.log("[WARNING] [$category] $message")
        }
    }

    /**
     * Log an info message
     */
    fun info(category: String, message: String) {
        if (isEnabled(LogLevel.INFO)) {
            logger?.atInfo()?.log("[$category] $message")
        }
    }

    /**
     * Log a verbose message (detailed operations)
     */
    fun verbose(category: String, message: String) {
        if (isEnabled(LogLevel.VERBOSE)) {
            logger?.atFiner()?.log("[VERBOSE] [$category] $message")
        }
    }

    /**
     * Log a debug message (very detailed)
     */
    fun debug(category: String, message: String) {
        if (isEnabled(LogLevel.DEBUG)) {
            logger?.atFine()?.log("[DEBUG] [$category] $message")
        }
    }

    /**
     * Log a separator line (only for VERBOSE and DEBUG)
     */
    fun separator(category: String) {
        if (isEnabled(LogLevel.VERBOSE)) {
            logger?.atInfo()?.log("[$category] ============================================")
        }
    }
}
