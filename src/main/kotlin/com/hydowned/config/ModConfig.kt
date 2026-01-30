package com.hydowned.config

import com.google.gson.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Configuration for a specific damage type affecting downed players.
 *
 * @property enabled Whether this damage type can affect downed players
 * @property damageMultiplier Multiplier applied to damage (default 0.6 = 40% reduction)
 */
data class DamageTypeConfig(
    val enabled: Boolean = false,
    val damageMultiplier: Double = 0.6  // 40% damage reduction by default
)

/**
 * Configuration for damage types allowed to affect downed players.
 * This allows fine-grained control over which damage sources can kill downed players
 * and how much damage they deal.
 */
data class AllowedDamage(
    val player: DamageTypeConfig = DamageTypeConfig(true),  // Player damage (PvP)
    val ai: DamageTypeConfig = DamageTypeConfig(true),      // AI damage
    val environment: DamageTypeConfig = DamageTypeConfig(),          // Environmental damage (fall, fire, etc.)
    val lava: DamageTypeConfig = DamageTypeConfig(enabled = true, damageMultiplier = 1.0) // Lava damage (prevents being stuck)
)

/**
 * Downed state configuration.
 */
data class DownedSettings(
    val timerSeconds: Int = 180,
    val deathOnTimeout: Boolean = true,
    val healthWhenDowned: Float = 0.30f,
    val allowMovement: Boolean = true,
    val applySlow: Boolean = true,
    val jumpForce: Float = 0.0f,
    val giveUpTicks: Int = 80,
    val aiRetargetRange: Double = 16.0,
    val allowedDamage: AllowedDamage = AllowedDamage()
)

/**
 * Revive configuration.
 */
data class ReviveSettings(
    val timerSeconds: Int = 10,
    val maxRange: Double = 2.0,
    val healOnReviveEnabled: Boolean = false,
    val healOnReviveHealth: Float = 0.30f
)

/**
 * Camera configuration for downed players.
 */
data class CameraSettings(
    val enabled: Boolean = false,
    val defaultCamera: Boolean = true,
    val firstPerson: Boolean = false,
    val positionX: Double = 0.0,
    val positionY: Double = 0.8,
    val positionZ: Double = 0.0
)

/**
 * UI/HUD configuration.
 */
data class UISettings(
    val enableSounds: Boolean = true,
    val enableProgressBar: Boolean = true,
    val showChatMessages: Boolean = false
)

/**
 * Main configuration class for HyDowned mod.
 *
 * Organized into logical sections using nested data classes.
 */
data class ModConfig(
    // Nested configuration sections
    val downed: DownedSettings = DownedSettings(),
    val revive: ReviveSettings = ReviveSettings(),
    val camera: CameraSettings = CameraSettings(),
    val ui: UISettings = UISettings(),
    val logLevel: String = "INFO"
) {
    companion object {
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(DamageTypeConfig::class.java, DamageTypeConfigDeserializer())
            .registerTypeAdapter(ModConfig::class.java, ModConfigDeserializer())
            .create()

        fun load(dataFolder: File): ModConfig {
            val configFile = File(dataFolder, "config.json")

            return if (configFile.exists()) {
                try {
                    // Check if migration will occur before loading
                    val willMigrate = try {
                        FileReader(configFile).use { reader ->
                            val originalJson = JsonParser.parseReader(reader).asJsonObject
                            !originalJson.has("downed") && originalJson.has("downedTimerSeconds")
                        }
                    } catch (e: Exception) {
                        false
                    }

                    // Load config (migration happens here if needed)
                    val config = FileReader(configFile).use { reader ->
                        gson.fromJson(reader, ModConfig::class.java)
                    }

                    // If migrated, backup old and save new
                    if (willMigrate) {
                        val backupFile = File(dataFolder, "config.json.backup")
                        configFile.copyTo(backupFile, overwrite = true)
                        com.hydowned.logging.Log.info("ModConfig", "Old config backed up to: config.json.backup")

                        config.save(dataFolder)
                        com.hydowned.logging.Log.info("ModConfig", "New config saved successfully")
                    }

                    config
                } catch (e: Exception) {
                    e.printStackTrace()
                    ModConfig().also { it.save(dataFolder) }
                }
            } else {
                ModConfig().also { it.save(dataFolder) }
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

    fun reload(dataFolder: File): ModConfig {
        return load(dataFolder)
    }
}
