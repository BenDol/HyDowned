package com.hydowned.config

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type

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
data class AllowedDownedDamage(
    val player: DamageTypeConfig = DamageTypeConfig(true),  // Player damage (PvP)
    val mob: DamageTypeConfig = DamageTypeConfig(true),     // Mob damage
    val environment: DamageTypeConfig = DamageTypeConfig(),          // Environmental damage (fall, fire, etc.)
    val lava: DamageTypeConfig = DamageTypeConfig(enabled = true, damageMultiplier = 1.0) // Lava damage (prevents being stuck)
)

/**
 * Custom deserializer for DamageTypeConfig to support backward compatibility.
 * Old format: "player": true/false (boolean)
 * New format: "player": { "enabled": true, "damageMultiplier": 0.6 }
 */
class DamageTypeConfigDeserializer : JsonDeserializer<DamageTypeConfig> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): DamageTypeConfig {
        return when {
            json.isJsonPrimitive && json.asJsonPrimitive.isBoolean -> {
                // Old format: simple boolean
                val enabled = json.asBoolean
                DamageTypeConfig(enabled = enabled, damageMultiplier = 0.6)
            }
            json.isJsonObject -> {
                // New format: object with enabled and damageMultiplier
                val obj = json.asJsonObject
                val enabled = obj.get("enabled")?.asBoolean ?: false
                val damageMultiplier = obj.get("damageMultiplier")?.asDouble ?: 0.6
                DamageTypeConfig(enabled = enabled, damageMultiplier = damageMultiplier)
            }
            else -> {
                // Fallback to default
                DamageTypeConfig()
            }
        }
    }
}

data class DownedConfig(
    val downedTimerSeconds: Int = 180,
    val reviveTimerSeconds: Int = 10,
    val downedSpeedMultiplier: Double = 0.1,
    val downedHealthPercent: Double = 0.2, // Health percentage when downed (20% = 0.2)
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
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(DamageTypeConfig::class.java, DamageTypeConfigDeserializer())
            .create()

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
