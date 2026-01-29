package com.hydowned.config

import com.google.gson.*
import com.hydowned.logging.Log
import java.lang.reflect.Type

/**
 * Custom GSON deserializer for ModConfig that handles backward compatibility
 * from the old flat config structure to the new nested structure.
 *
 * Old format (flat):
 * - downedTimerSeconds, reviveTimerSeconds, downedHealthPercent, etc.
 * - All fields at root level
 *
 * New format (nested):
 * - downed: { downedTimerSeconds, healthWhenDowned, ... }
 * - revive: { timerSeconds, maxRange, ... }
 * - camera: { ... }
 * - ui: { ... }
 */
class ModConfigDeserializer : JsonDeserializer<ModConfig> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ModConfig {
        val jsonObject = json.asJsonObject

        // Detect format: new format has "downed" object, old has root-level fields
        val isOldFormat = !jsonObject.has("downed") && jsonObject.has("downedTimerSeconds")

        return if (isOldFormat) {
            deserializeOldFormat(jsonObject)
        } else {
            // Use default GSON for new format
            Gson().fromJson(json, ModConfig::class.java)
        }
    }

    private fun deserializeOldFormat(json: JsonObject): ModConfig {
        Log.info("ModConfig", "========================================")
        Log.info("ModConfig", "CONFIG MIGRATION DETECTED")
        Log.info("ModConfig", "========================================")
        Log.info("ModConfig", "Old flat config structure found - migrating to nested structure")

        // Extract SAME NAME fields (location changed)
        val downedTimerSeconds = json.get("downedTimerSeconds")?.asInt ?: 180
        val enableSounds = json.get("enableSounds")?.asBoolean ?: true
        val logLevel = json.get("logLevel")?.asString ?: "INFO"

        val allowedDamage = if (json.has("allowedDownedDamage")) {
            Gson().fromJson(json.get("allowedDownedDamage"), AllowedDamage::class.java)
        } else {
            AllowedDamage()
        }

        // Extract RENAMED fields
        val healthWhenDowned = json.get("downedHealthPercent")?.asFloat ?: 0.2f
        val healOnReviveHealth = json.get("reviveHealthPercent")?.asFloat ?: 0.2f
        val reviveTimerSeconds = json.get("reviveTimerSeconds")?.asInt ?: 10
        val reviveMaxRange = json.get("reviveRange")?.asDouble ?: 2.0
        val enableProgressBar = json.get("enableActionBar")?.asBoolean ?: true

        // NEW fields (use defaults)
        val deathOnTimeout = true
        val allowMovement = true  // Match current default in DownedSettings
        val applySlow = true
        val healOnReviveEnabled = false  // Match current default in ReviveSettings

        // Camera settings (all new)
        val camera = CameraSettings(
            enabled = false,  // Match current default
            defaultCamera = true,
            firstPerson = false,
            positionX = 0.0,
            positionY = 0.8,
            positionZ = 0.0
        )

        // Log removed fields
        val removedFields = mutableListOf<String>()
        if (json.has("downedMode")) removedFields.add("downedMode")
        if (json.has("downedAnimationType")) removedFields.add("downedAnimationType")
        if (json.has("multipleReviversMode")) removedFields.add("multipleReviversMode")
        if (json.has("reviveSpeedupPerPlayer")) removedFields.add("reviveSpeedupPerPlayer")
        if (json.has("invisibilityMode")) removedFields.add("invisibilityMode")
        if (json.has("downedSpeedMultiplier")) removedFields.add("downedSpeedMultiplier")

        if (removedFields.isNotEmpty()) {
            Log.info("ModConfig", "Ignored removed fields: ${removedFields.joinToString(", ")}")
            Log.info("ModConfig", "These features have been removed in this version")
        }

        // Log migration details
        Log.info("ModConfig", "Mapped renamed fields:")
        Log.info("ModConfig", "  downedHealthPercent → downed.healthWhenDowned")
        Log.info("ModConfig", "  reviveHealthPercent → revive.healOnReviveHealth")
        Log.info("ModConfig", "  reviveTimerSeconds → revive.timerSeconds")
        Log.info("ModConfig", "  reviveRange → revive.maxRange")
        Log.info("ModConfig", "  enableActionBar → ui.enableProgressBar")
        Log.info("ModConfig", "  allowedDownedDamage → downed.allowedDamage")

        Log.info("ModConfig", "Added new fields with defaults:")
        Log.info("ModConfig", "  downed.deathOnTimeout = $deathOnTimeout")
        Log.info("ModConfig", "  downed.allowMovement = $allowMovement")
        Log.info("ModConfig", "  downed.applySlow = $applySlow")
        Log.info("ModConfig", "  revive.healOnReviveEnabled = $healOnReviveEnabled")
        Log.info("ModConfig", "  camera.* (all camera settings)")

        Log.info("ModConfig", "Migration complete - config will be auto-saved in new format")
        Log.info("ModConfig", "========================================")

        return ModConfig(
            downed = DownedSettings(
                timerSeconds = downedTimerSeconds,
                deathOnTimeout = deathOnTimeout,
                healthWhenDowned = healthWhenDowned,
                allowMovement = allowMovement,
                applySlow = applySlow,
                allowedDamage = allowedDamage
            ),
            revive = ReviveSettings(
                timerSeconds = reviveTimerSeconds,
                maxRange = reviveMaxRange,
                healOnReviveEnabled = healOnReviveEnabled,
                healOnReviveHealth = healOnReviveHealth
            ),
            camera = camera,
            ui = UISettings(
                enableSounds = enableSounds,
                enableProgressBar = enableProgressBar
            ),
            logLevel = logLevel
        )
    }
}

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