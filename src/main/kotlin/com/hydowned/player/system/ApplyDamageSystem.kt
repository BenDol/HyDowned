package com.hydowned.player.system

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.entity.EntityUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.ModPlugin
import com.hydowned.player.aspect.OtherAspect
import com.hydowned.logging.Log
import com.hydowned.manager.Managers

/**
 * Damage interceptor system.
 *
 * Runs BEFORE ApplyDamage to intercept lethal damage and put player in downed state.
 * Also handles damage from/to downed players.
 */
class ApplyDamageSystem(val managers: Managers) : DamageSystems.ApplyDamage() {

    companion object {
        private val DEPENDENCIES: Set<Dependency<EntityStore>> = setOf(
            SystemDependency(Order.BEFORE, DamageSystems.ApplyDamage::class.java)
        )
    }

    override fun getDependencies(): Set<Dependency<EntityStore>> {
        return DEPENDENCIES
    }

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        damage: Damage
    ) {
        // Check if attacker is downed (cancel their damage)
        val source = damage.source
        if (source is Damage.EntitySource) {
            val sourceRef = source.ref
            if (sourceRef.isValid) {
                val attacker = store.getComponent(sourceRef, Player.getComponentType())
                if (attacker != null) {
                    val attackerRevivePlayer = managers.playerManager.get(attacker)
                    if (attackerRevivePlayer?.asDownable?.isDowned() == true) {
                        damage.isCancelled = true
                        damage.amount = 0.0f
                        return
                    }
                }
            }
        }

        // Get damaged player
        val holder = EntityUtils.toHolder(index, archetypeChunk)
        val player = holder.getComponent(Player.getComponentType())
        val playerRef = holder.getComponent(PlayerRef.getComponentType())

        if (player == null || playerRef == null || player.gameMode == GameMode.Creative) {
            return
        }

        val ref = playerRef.reference ?: return
        if (!ref.isValid) {
            return
        }

        val modPlayer = managers.playerManager.get(player) ?: return
        val downable = modPlayer.asDownable
        val config = ModPlugin.instance!!.config

        // Handle damage TO downed players
        if (downable.isDowned()) {
            // Check if we should pass damage through
            if (downable.allowDamage) {
                downable.allowDamage = false
                return
            }

            // Check config for allowing damage
            val allowPlayerDamage = config.downed.allowedDamage.player.enabled
            val allowAIDamage = config.downed.allowedDamage.ai.enabled

            val attacker = if (source is Damage.EntitySource) {
                val attackerRef = source.ref
                if (attackerRef.isValid) {
                    store.getComponent(attackerRef, Player.getComponentType())
                } else null
            } else null

            if (attacker != null && allowPlayerDamage) {
                downable.allowDamage = true
                return
            }

            if (attacker == null && allowAIDamage) {
                downable.allowDamage = true
                return
            }

            // Cancel damage by default
            damage.isCancelled = true
            return
        }

        // Intercept LETHAL damage and put player in downed state
        val aggressor = if (source is Damage.EntitySource) {
            val attackerRef = source.ref
            if (attackerRef.isValid) {
                val attacker = store.getComponent(attackerRef, Player.getComponentType())
                if (attacker != null) {
                    managers.playerManager.get(attacker)?.asAggressor
                } else null
            } else null
        } else {
            val damageCause = DamageCause.getAssetMap().getAsset(damage.damageCauseIndex)
            OtherAspect.create(damageCause?.id ?: "unknown")
        } ?: OtherAspect.create("unknown")

        // Check if player is already dead
        val isDead = archetypeChunk.archetype.contains(DeathComponent.getComponentType())
        if (isDead) {
            return
        }

        // Check if damage would be lethal
        val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
        if (entityStatMap != null) {
            val healthStat = DefaultEntityStatTypes.getHealth()
            val healthValue = entityStatMap.get(healthStat)

            if (healthValue != null) {
                val newValue = healthValue.get() - damage.amount

                if (newValue <= healthValue.min) {
                    // Damage would be lethal - put player in downed state instead
                    Log.info("DamageInterceptorSystem",
                        "${player.displayName} would die from ${damage.amount} damage - downing instead")

                    managers.downManager.down(downable, aggressor)
                    entityStatMap.setStatValue(healthStat, 1.0f)
                    damage.isCancelled = true
                }
            }
        }
    }
}
