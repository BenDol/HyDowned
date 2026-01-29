package com.hydowned.logging

import com.hypixel.hytale.logger.HytaleLogger
import java.util.logging.Level

/**
 * Logging system for HyDowned mod.
 *
 * Uses Hytale's native logging system. Configure log levels in the server's config.json:
 * ```json
 * "LogLevels": {
 *   "com.hydowned": "FINER"  // or INFO, WARNING, SEVERE, FINE, etc.
 * }
 * ```
 *
 * Log Level Mapping:
 * - error()   -> SEVERE (critical errors)
 * - warning() -> WARNING (potential issues)
 * - info()    -> INFO (important messages)
 * - verbose() -> FINER (detailed operations)
 * - debug()   -> FINE (very detailed debugging)
 *
 * Usage:
 * ```
 * Log.info("System", "Player entered downed state")
 * if (Log.isEnabled(Level.FINE)) {
 *     Log.debug("System", "Expensive: ${expensiveOperation()}")
 * }
 * Log.error("System", "Failed to save data: ${e.message}")
 * ```
 */
object Log {

    private var logger: ModLogger? = null

    /**
     * Initialize with Hytale logger (should be called from plugin setup)
     */
    fun init(pluginLogger: HytaleLogger, logLevel: String = "INFO") {
        logger = ModLogger(pluginLogger, logLevel)
        logger?.atInfo()?.log("HyDowned logging system initialized with level: $logLevel")

        // Force lazy initialization of backend logger (which sets log level from config)
        logger?.isEnabled(Level.INFO)
    }

    /**
     * Check if a specific log level is enabled.
     * Use this to avoid expensive string operations when logging is disabled.
     *
     * Example:
     * ```
     * if (Log.isEnabled(Level.FINE)) {
     *     Log.debug("System", "Expensive data: ${expensiveToString()}")
     * }
     * ```
     */
    fun isEnabled(level: Level): Boolean {
        return logger?.isEnabled(level) ?: false
    }

    /**
     * Log an error message (SEVERE level)
     */
    fun error(category: String, message: String) {
        logger?.atSevere()?.log("[$category] $message")
    }

    /**
     * Log a warning message (WARNING level)
     */
    fun warning(category: String, message: String) {
        logger?.atWarning()?.log("[$category] $message")
    }

    /**
     * Log an info message (INFO level)
     */
    fun info(category: String, message: String) {
        logger?.atInfo()?.log("[$category] $message")
    }

    /**
     * Log a verbose message (FINER level - detailed operations)
     */
    fun finer(category: String, message: String) {
        logger?.atFiner()?.log("[$category] $message")
    }

    /**
     * Log a fine message (FINE level - very detailed)
     */
    fun fine(category: String, message: String) {
        logger?.atFine()?.log("[$category] $message")
    }

    /**
     * Log a debug message (FINE level - very detailed)
     */
    fun debug(category: String, message: String) = fine(category, message)

    /**
     * Log a separator line (INFO level for visibility)
     */
    fun separator(category: String, logLevel: Level = Level.INFO) {
        logger?.at(logLevel)?.log("[$category] ============================================")
    }
}
