package com.hydowned.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Configuration for damage types allowed to affect downed players.
 * This allows fine-grained control over which damage sources can kill downed players.
 */
data class AllowedDownedDamage(
    val player: Boolean = false,  // Allow player damage (PvP)
    val mob: Boolean = false,      // Allow mob damage
    val environment: Boolean = false, // Allow environmental damage (fall, fire, etc.)
    val lava: Boolean = true       // Allow lava damage (prevents being stuck in lava)
)

data class DownedConfig(
    val downedTimerSeconds: Int = 180,
    val reviveTimerSeconds: Int = 10,
    val downedSpeedMultiplier: Double = 0.1,
    val reviveHealthPercent: Double = 0.2,
    val reviveRange: Double = 2.0,
    val multipleReviversMode: String = "SPEEDUP",
    val reviveSpeedupPerPlayer: Double = 0.5,
    val enableParticles: Boolean = true,
    val enableSounds: Boolean = true,
    val enableActionBar: Boolean = true,
    val invisibilityMode: String = "SCALE", // SCALE or INVISIBLE
    val downedMode: String = "PLAYER", // PHANTOM or PLAYER
    val logLevel: String = "INFO", // SEVERE, WARNING, INFO, FINE, FINER, FINEST
    val allowedDownedDamage: AllowedDownedDamage = AllowedDownedDamage() // Fine-grained damage control
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(dataFolder: File): DownedConfig {
            val configFile = File(dataFolder, "config.json")

            return if (configFile.exists()) {
                try {
                    FileReader(configFile).use { reader ->
                        gson.fromJson(reader, DownedConfig::class.java)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    DownedConfig().also { it.save(dataFolder) }
                }
            } else {
                DownedConfig().also { it.save(dataFolder) }
            }
        }
    }

    fun save(dataFolder: File) {
        dataFolder.mkdirs()
        val configFile = File(dataFolder, "config.json")

        try {
            FileWriter(configFile).use { writer ->
                gson.toJson(this, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reload(dataFolder: File): DownedConfig {
        return load(dataFolder)
    }

    val isSpeedupMode: Boolean
        get() = multipleReviversMode.equals("SPEEDUP", ignoreCase = true)

    val useScaleMode: Boolean
        get() = invisibilityMode.equals("SCALE", ignoreCase = true)

    val useInvisibleMode: Boolean
        get() = invisibilityMode.equals("INVISIBLE", ignoreCase = true)

    val usePhantomMode: Boolean
        get() = downedMode.equals("PHANTOM", ignoreCase = true)

    val usePlayerMode: Boolean
        get() = downedMode.equals("PLAYER", ignoreCase = true)
}
