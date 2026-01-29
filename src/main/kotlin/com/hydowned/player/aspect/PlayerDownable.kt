package com.hydowned.player.aspect

import com.hypixel.hytale.protocol.ClientCameraView
import com.hypixel.hytale.protocol.Position
import com.hypixel.hytale.protocol.ServerCameraSettings
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
import com.hypixel.hytale.server.core.entity.AnimationUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.modules.entity.EntityModule
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.protocol.AnimationSlot
import com.hydowned.ModPlugin
import com.hydowned.config.ModConfig
import com.hydowned.aspect.Aspect
import com.hydowned.aspect.Downable
import com.hydowned.aspect.Reviver
import com.hydowned.logging.Log
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes

/**
 * Implementation of Downable for players.
 *
 * Handles all downed state logic including:
 * - Animations (Sleep animation)
 * - Camera (custom third-person view)
 * - Movement (slowness effect or complete lockdown)
 * - Health management
 * - Revive logic
 */
class PlayerDownable(
    player: Player,
    playerRef: PlayerRef
) : PlayerAspect(player, playerRef), Downable {

    private val managers = ModPlugin.instance!!.managers
    private var aggressor: Aspect? = null

    var passDamage: Boolean = false
    var giveUpTicks: Int = -1

    // Original movement values for restoration
    private var jumpForce: Float = 7.5f
    private var speedBase: Float = 5.5f

    override fun getDisplayName(): String {
        return player.displayName ?: "Unknown"
    }

    override fun isDowned(): Boolean {
        return managers.downManager.isDowned(this)
    }

    override fun isDead(): Boolean {
        return false
    }

    override fun isAlive(): Boolean {
        return true
    }

    override fun getDownProgress(): Float {
        return (1.0 - getTimeRemaining() / getDownDuration()).toFloat()
    }

    override fun getDownDuration(): Double {
        val config = ModPlugin.instance!!.config
        return config.downed.timerSeconds.toDouble() * 20.0
    }

    override fun getTimeRemaining(): Double {
        return managers.downManager.getTimeLeft(this)
    }

    override fun getAggressor(): Aspect? {
        return aggressor
    }

    override fun getCurrentReviver(): Reviver? {
        return managers.reviveManager.getReviverOf(this)
    }

    override fun onDown(aggressor: Aspect) {
        val config = ModPlugin.instance!!.config
        this.aggressor = aggressor

        Log.finer("PlayerDownable",
            "${getDisplayName()} went down (aggressor: ${aggressor.getDisplayName()})")

        // Start Sleep animation
        startDownedAnimation()

        // Set health to configured downed health
        setHealthPercent(config.downed.healthWhenDowned)

        // Setup camera
        setupCamera(config)

        // Apply slowness effect
        setDownedEffect(getDownDuration().toFloat(), config.downed.applySlow,
            "on down: ${getDownDuration()}")

        // Block movement if configured
        if (!config.downed.allowMovement) {
            disableMovement()
        }
    }

    override fun onDeath() {
        Log.finer("PlayerDownable", "${getDisplayName()} died while downed")
        stopDown()

        // Add DeathComponent to trigger actual death
        player.world?.execute {
            val reference = player.reference ?: return@execute
            val store = reference.store
            val damageCause = DamageCause.getAssetMap().getAsset("ENVIRONMENT") ?: return@execute
            val damage = Damage(
                Damage.EnvironmentSource("downed"),
                damageCause,
                2.147483E9f
            )
            DeathComponent.tryAddComponent(store, reference, damage)
        }
    }

    override fun onRevived() {
        Log.finer("PlayerDownable", "${getDisplayName()} was saved/revived")
        stopDown()
    }

    override fun onCancelDown() {
        Log.finer("PlayerDownable", "${getDisplayName()} downed state canceled")
        stopDown()
    }

    private fun stopDown() {
        // Stop animation
        stopDownedAnimation()

        // Reset camera
        playerRef.packetHandler.writeNoCache(
            SetServerCamera(ClientCameraView.Custom, false, null))

        // Hide HUD
        val modPlayer = managers.playerManager.get(player)
        if (modPlayer != null) {
            managers.hudManager.showHud(modPlayer, modPlayer, false)
        }

        // Clear time component
        managers.downManager.storePlayerTimeLeft(this, 0)

        // Remove slowness effect and restore movement
        player.world?.execute {
            val reference = playerRef.reference ?: return@execute
            val store = reference.store

            // Remove slowness effect
            val effectController = store.getComponent(
                reference,
                EntityModule.get().effectControllerComponentType
            )

            if (effectController != null) {
                val effectName = if (managers.config.downed.applySlow) "DownedWithSlow" else "Downed"
                val downedEffect = EntityEffect.getAssetStore().assetMap.getAsset(effectName)
                if (downedEffect != null) {
                    effectController.removeEffect(
                        reference,
                        EntityEffect.getAssetMap().getIndex(downedEffect.id),
                        store
                    )
                }
            }

            // Restore movement
            val config = ModPlugin.instance!!.config
            if (!config.downed.allowMovement) {
                val movementManager = store.getComponent(
                    reference,
                    MovementManager.getComponentType()
                )

                if (movementManager != null) {
                    movementManager.settings.baseSpeed = if (speedBase == 0.0f) 5.5f else speedBase
                    movementManager.settings.jumpForce = if (jumpForce == 0.0f) 11.8f else jumpForce
                    movementManager.update(playerRef.packetHandler)
                }
            }
        }

        // Heal on revive if configured
        val config = ModPlugin.instance!!.config
        if (config.revive.healOnReviveEnabled) {
            setHealthPercent(config.revive.healOnReviveHealth)
        }

        passDamage = false
    }

    override fun canBeRevivedBy(reviver: Reviver): Boolean {
        // Basic checks
        return isDowned() && getCurrentReviver() == null
    }

    override fun canDie(): Boolean {
        return true
    }

    override fun tick() {
        // Called every tick while downed
        // Can be used for custom logic if needed
    }

    fun getMaxGiveUpTicks(): Int {
        return 100
    }

    fun setDownedEffect(time: Float, applySlow: Boolean, reason: String) {
        player.world?.execute {
            val reference = playerRef.reference
            if (reference == null) {
                Log.warning("PlayerDownable", "setSlowEffect: reference is null for ${getDisplayName()}")
                return@execute
            }

            val store = reference.store
            val effectController = store.getComponent(
                reference,
                EntityModule.get().effectControllerComponentType
            )

            if (effectController == null) {
                Log.warning("PlayerDownable",
                    "setSlowEffect: effectController is null for ${getDisplayName()}")
                return@execute
            }

            val effectName = if (applySlow) "DownedWithSlow" else "Downed"

            val downedEffect = EntityEffect.getAssetStore().assetMap.getAsset(effectName)
            if (downedEffect == null) {
                Log.warning("PlayerDownable", "setDownedEffect: downedEffect is null for ${getDisplayName()}")
                return@execute
            }

            val duration = time / 20.0f
            Log.info("PlayerDownable",
                "Applying downed effect to ${getDisplayName()}: duration=${duration}s, reason=$reason")

            effectController.addEffect(
                reference,
                downedEffect,
                duration,
                downedEffect.getOverlapBehavior(),
                store
            )
        }
    }

    private fun startDownedAnimation() {
        val reference = playerRef.reference ?: return
        val store = reference.store
        player.world?.execute {
            AnimationUtils.playAnimation(
                reference,
                AnimationSlot.Movement,
                "Sleep",
                true, // looping
                store
            )
        }
    }

    private fun stopDownedAnimation() {
        val reference = playerRef.reference ?: return
        val store = reference.store
        player.world?.execute {
            AnimationUtils.stopAnimation(
                reference,
                AnimationSlot.Movement,
                store
            )
        }
    }

    private fun setupCamera(config: ModConfig) {
        val settings = ServerCameraSettings()
        settings.isFirstPerson = false
        settings.positionOffset = Position(0.0, 0.8, 0.0)

        if (config.camera.defaultCamera) {
            playerRef.packetHandler.writeNoCache(
                SetServerCamera(ClientCameraView.Custom, false, settings)
            )
        } else if (config.camera.enabled) {
            settings.isFirstPerson = config.camera.firstPerson
            settings.positionOffset = Position(
                config.camera.positionX,
                config.camera.positionY,
                config.camera.positionZ
            )
            playerRef.packetHandler.writeNoCache(
                SetServerCamera(ClientCameraView.Custom, false, settings)
            )
        }
    }

    private fun disableMovement() {
        player.world?.execute {
            val worldObj = player.world ?: return@execute
            val reference = playerRef.reference ?: return@execute
            val store = worldObj.entityStore.store

            val movementManager = store.getComponent(
                reference,
                MovementManager.getComponentType()
            ) ?: return@execute

            // Save original values
            jumpForce = movementManager.settings.jumpForce
            speedBase = movementManager.settings.baseSpeed

            // Set to zero
            movementManager.settings.baseSpeed = 0.0f
            movementManager.settings.jumpForce = 0.0f
            movementManager.update(playerRef.packetHandler)
        }
    }

    private fun setHealthPercent(healthPercent: Float) {
        player.world?.execute {
            val reference = playerRef.reference ?: return@execute
            val store = reference.store

            val entityStatMap = store.getComponent(
                reference,
                EntityStatMap.getComponentType()
            ) ?: return@execute

            val healthStat = DefaultEntityStatTypes.getHealth()
            val healthValue = entityStatMap.get(healthStat) ?: return@execute

            // Calculate health based on percentage of max health
            val targetHealth = healthValue.max * healthPercent.coerceIn(0.0f, 1.0f)

            entityStatMap.setStatValue(healthStat, targetHealth)
        }
    }

    private fun setHealth(health: Int) {
        player.world?.execute {
            val reference = playerRef.reference ?: return@execute
            val store = reference.store

            val entityStatMap = store.getComponent(
                reference,
                EntityStatMap.getComponentType()
            ) ?: return@execute

            entityStatMap.setStatValue(
                DefaultEntityStatTypes.getHealth(),
                health.toFloat()
            )
        }
    }
}
