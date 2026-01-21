package com.hydowned.util

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend
import java.util.logging.Level

/**
 * Wrapper around HytaleLogger that exposes log level checking.
 *
 * HytaleLogger's backend and isLoggable() methods are private/protected,
 * so we use reflection to access the underlying HytaleLoggerBackend for level checking.
 */
class HyDownedLogger(private val hytaleLogger: HytaleLogger, private val configuredLogLevel: String = "INFO") {

    private val backendLogger: HytaleLoggerBackend by lazy {
        // Use reflection to access the private backend field
        val backendField = HytaleLogger::class.java.getDeclaredField("backend")
        backendField.isAccessible = true
        val backend = backendField.get(hytaleLogger) as HytaleLoggerBackend

        // Parse and set logger level from plugin config
        val level = try {
            Level.parse(configuredLogLevel.uppercase())
        } catch (e: IllegalArgumentException) {
            hytaleLogger.atWarning().log("Invalid log level '$configuredLogLevel', defaulting to INFO")
            Level.INFO
        }
        backend.level = level

        backend
    }

    /**
     * Check if a specific log level is enabled.
     * Use this to avoid expensive string operations when logging is disabled.
     *
     * Example:
     * ```
     * if (logger.isEnabled(Level.FINE)) {
     *     Log.debug("System", "Expensive operation: ${expensiveToString()}")
     * }
     * ```
     */
    fun isEnabled(level: Level): Boolean {
        // HytaleLoggerBackend extends Logger, so we can use isLoggable
        return backendLogger.isLoggable(level)
    }

    /**
     * Get the underlying HytaleLogger for direct logging.
     */
    fun getHytaleLogger(): HytaleLogger {
        return hytaleLogger
    }

    // Convenience methods that delegate to HytaleLogger

    fun at(level: Level) = hytaleLogger.at(level)
    fun atSevere() = hytaleLogger.atSevere()
    fun atWarning() = hytaleLogger.atWarning()
    fun atInfo() = hytaleLogger.atInfo()
    fun atFiner() = hytaleLogger.atFiner()
    fun atFine() = hytaleLogger.atFine()
    fun atFinest() = hytaleLogger.atFinest()
}
