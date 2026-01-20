package com.hydowned.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class DownedConfig(
    val downedTimerSeconds: Int = 180,
    val reviveTimerSeconds: Int = 10,
    val downedSpeedMultiplier: Double = 0.1,
    val reviveHealthPercent: Double = 0.2,
    val reviveRange: Double = 2.0,
    val downedAnimationType: String = "LAYING",
    val multipleReviversMode: String = "SPEEDUP",
    val reviveSpeedupPerPlayer: Double = 0.5,
    val enableParticles: Boolean = true,
    val enableSounds: Boolean = true,
    val enableActionBar: Boolean = true,
    val invisibilityMode: String = "SCALE", // SCALE or INVISIBLE
    val logLevel: String = "VERBOSE" // ERROR, WARNING, INFO, VERBOSE, DEBUG
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

    val isLayingAnimation: Boolean
        get() = downedAnimationType.equals("LAYING", ignoreCase = true)

    val useScaleMode: Boolean
        get() = invisibilityMode.equals("SCALE", ignoreCase = true)

    val useInvisibleMode: Boolean
        get() = invisibilityMode.equals("INVISIBLE", ignoreCase = true)
}
