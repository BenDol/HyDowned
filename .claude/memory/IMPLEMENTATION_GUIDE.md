# HyDowned - Death Interception Implementation Guide

## How Death Works in Hytale

Based on reverse engineering the decompiled `HytaleServer.jar`, here's how Hytale's death system works:

### Death Flow

1. **Damage is Applied** (`DamageSystems.ApplyDamage` - line 1166):
   ```java
   float newValue = entityStatMapComponent.subtractStatValue(healthStat, damage.getAmount());
   if (newValue <= healthValue.getMin()) {
       DeathComponent.tryAddComponent(commandBuffer, archetypeChunk.getReferenceTo(index), damage);
   }
   ```

2. **DeathComponent is Added**: When health reaches 0, `DeathComponent` is added to the entity

3. **Death Systems React**: All `OnDeathSystem` classes react to the `DeathComponent`:
   - `PlayerDeathScreen` - Shows "You Died!" screen
   - `DropPlayerDeathItems` - Drops items
   - `PlayerDeathMarker` - Marks death location
   - `KillFeed` - Broadcasts kill feed
   - `ClearHealth` - Sets health to 0
   - etc.

### Key Classes

- **`DamageSystems.ApplyDamage`**: Applies damage and triggers death when health <= 0
- **`DeathComponent`**: ECS component that marks an entity as dead
- **`OnDeathSystem`**: Base class for systems that react to death
- **`DamageEventSystem`**: Base class for systems that process damage events

## Solution: Custom DamageEventSystem

We need to create a custom `DamageEventSystem` that runs **BEFORE** `ApplyDamage` and intercepts lethal damage.

### Approach

1. Create a `DamageEventSystem` that listens for damage events on players
2. Calculate if the damage would be lethal (health - damage <= 0)
3. If lethal and player not already downed:
   - Modify damage to leave player at 1 HP
   - Put player in "downed" state
   - Store death information for later

### Implementation Steps

## Step 1: Create DownedComponent

This ECS component marks a player as being in the downed state.

**File**: `src/main/kotlin/com/hydowned/components/DownedComponent.kt`

```kotlin
package com.hydowned.components

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.HyDownedPlugin

/**
 * Component that marks an entity as being in a "downed" state
 * This replaces death - player stays alive at 1 HP and can be revived
 */
class DownedComponent(
    var downedTimeRemaining: Int,
    var reviverPlayerIds: MutableSet<String> = mutableSetOf(),
    var reviveTimeRemaining: Double = 0.0,
    val downedAt: Long = System.currentTimeMillis(),
    var downedLocation: Vector3d? = null
) : Component<EntityStore> {

    companion object {
        fun getComponentType(): ComponentType<EntityStore, DownedComponent> {
            return HyDownedPlugin.instance!!.getDownedComponentType()
        }
    }

    override fun clone(): Component<EntityStore> {
        return DownedComponent(
            downedTimeRemaining,
            reviverPlayerIds.toMutableSet(),
            reviveTimeRemaining,
            downedAt,
            downedLocation?.clone()
        )
    }
}
```

## Step 2: Create Death Interception System

This system intercepts damage that would kill the player and puts them in downed state instead.

**File**: `src/main/kotlin/com/hydowned/systems/DownedDeathInterceptor.kt`

```kotlin
package com.hydowned.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import java.util.Set
import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Intercepts damage that would kill a player and puts them in downed state instead
 *
 * This system runs in the FilterDamageGroup BEFORE ApplyDamage, so we can:
 * 1. Check if damage would be lethal
 * 2. Modify damage to leave player at 1 HP
 * 3. Add DownedComponent instead of letting DeathComponent be added
 */
class DownedDeathInterceptor(
    private val config: DownedConfig
) : DamageEventSystem() {

    private val query = Query.and(
        Player.getComponentType(),
        EntityStatMap.getComponentType(),
        TransformComponent.getComponentType()
    )

    // Run in FilterDamageGroup, BEFORE ApplyDamage
    private val dependencies = setOf(
        SystemDependency<EntityStore>(Order.BEFORE, DamageSystems.ApplyDamage::class.java)
    )

    @Nonnull
    override fun getQuery(): Query<EntityStore> = query

    @Nullable
    override fun getGroup(): SystemGroup<EntityStore> {
        return DamageModule.get().getFilterDamageGroup()
    }

    @Nonnull
    override fun getDependencies(): Set<Dependency<EntityStore>> = dependencies

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        damage: Damage
    ) {
        val ref = archetypeChunk.getReferenceTo(index)

        // Skip if player already downed
        if (commandBuffer.getArchetype(ref).contains(DownedComponent.getComponentType())) {
            return
        }

        // Get health stats
        val entityStatMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType())
            ?: return
        val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth()) ?: return
        val currentHealth = healthStat.get()

        // Calculate if this damage would be lethal
        val newHealth = currentHealth - damage.amount

        if (newHealth <= healthStat.min) {
            // This damage would kill the player - intercept it!

            // Modify damage to leave player at 1 HP instead of 0
            val modifiedDamage = currentHealth - 1.0f
            damage.amount = modifiedDamage

            // Get location for downed state
            val transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType())
            val location = transform?.position?.clone()

            // Add downed component instead of letting death happen
            val downedComponent = DownedComponent(
                downedTimeRemaining = config.downedTimerSeconds,
                reviverPlayerIds = mutableSetOf(),
                reviveTimeRemaining = 0.0,
                downedAt = System.currentTimeMillis(),
                downedLocation = location
            )

            commandBuffer.addComponent(ref, DownedComponent.getComponentType(), downedComponent)

            // Log for debugging
            println("[HyDowned] Player entered downed state (intercepted lethal damage)")

            // TODO: Apply visual effects (animation, particles, etc.)
            // TODO: Apply movement speed reduction
            // TODO: Send feedback message to player
        }
    }
}
```

## Step 3: Register the Component and System

Update `HyDownedPlugin.kt` to register the component and system.

**File**: `src/main/kotlin/com/hydowned/HyDownedPlugin.kt`

```kotlin
package com.hydowned

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.state.DownedStateManager
import com.hydowned.systems.DownedDeathInterceptor
import com.hydowned.timers.DownedTimerTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * HyDowned - Downed State Mod for Hytale
 *
 * Replaces player death with a "downed" state where players can be revived by teammates.
 */
class HyDownedPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        var instance: HyDownedPlugin? = null
    }

    private lateinit var config: DownedConfig
    private lateinit var stateManager: DownedStateManager
    private lateinit var downedComponentType: ComponentType<EntityStore, DownedComponent>
    private var timerTask: ScheduledFuture<*>? = null

    fun getDownedComponentType(): ComponentType<EntityStore, DownedComponent> {
        return downedComponentType
    }

    override fun setup() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Setup Phase")
        println("[HyDowned] ============================================")

        instance = this

        // Initialize configuration
        val pluginDataFolder = java.io.File("plugins/HyDowned")
        try {
            config = DownedConfig.load(pluginDataFolder)
            println("[HyDowned] ✓ Configuration loaded")
            println("[HyDowned]   - Downed Timer: ${config.downedTimerSeconds}s")
            println("[HyDowned]   - Revive Timer: ${config.reviveTimerSeconds}s")
            println("[HyDowned]   - Downed Speed: ${config.downedSpeedMultiplier * 100}%")
        } catch (e: Exception) {
            println("[HyDowned] ✗ Failed to load configuration: ${e.message}")
            e.printStackTrace()
            throw e
        }

        // Initialize state manager
        stateManager = DownedStateManager(config)
        println("[HyDowned] ✓ State manager initialized")

        // Register DownedComponent with ECS
        downedComponentType = getEntityStoreRegistry().registerComponent(
            DownedComponent::class.java,
            "HyDowned"
        ) { DownedComponent(0) }
        println("[HyDowned] ✓ DownedComponent registered")

        // Register death interception system
        getEntityStoreRegistry().registerSystem(DownedDeathInterceptor(config))
        println("[HyDowned] ✓ Death interception system registered")

        println("[HyDowned] ============================================")
    }

    override fun start() {
        println("[HyDowned] ============================================")
        println("[HyDowned] Start Phase")
        println("[HyDowned] ============================================")

        // TODO: Register commands when command API is understood
        // TODO: Register event listeners (player interact, player quit)
        // TODO: Start timer task for downed state countdown

        println("[HyDowned] ============================================")
        println("[HyDowned] ✓ HyDowned plugin started successfully!")
        println("[HyDowned] Status: Death interception active")
        println("[HyDowned] ============================================")
    }

    override fun shutdown() {
        println("[HyDowned] Shutting down...")

        // Cancel timer task if running
        timerTask?.cancel(false)
        timerTask = null

        // Execute death for any remaining downed players
        stateManager.cleanup()

        instance = null
        println("[HyDowned] Shutdown complete")
    }
}
```

## Step 4: Test the Implementation

1. **Build the mod**:
   ```bash
   ./gradlew build
   ```

2. **Deploy to Hytale**:
   ```bash
   ./gradlew deployToHytale
   ```

3. **Test in-game**:
   - Close Hytale if running
   - Start Hytale and load a world
   - Take fatal damage (fall, lava, enemy attack)
   - You should see:
     - `[HyDowned] Player entered downed state (intercepted lethal damage)` in logs
     - Player stays alive at 1 HP instead of dying
     - No death screen appears

## Next Steps

After confirming death interception works:

1. **Add visual feedback**:
   - Downed animation (lying on ground)
   - Movement speed reduction
   - Particle effects

2. **Implement revive mechanic**:
   - Player interaction system
   - Revive progress tracking
   - Multiple revivers support

3. **Add timer system**:
   - Countdown from downed state
   - Execute actual death when timer expires
   - Display timer to player

4. **Polish**:
   - Sound effects
   - Action bar messages
   - Configurable options

## Key Insights

### Why This Works

1. **ECS Architecture**: Hytale uses Entity Component System, not traditional events
2. **Death Trigger**: Death happens when `DeathComponent` is added to an entity
3. **Damage Pipeline**: Damage flows through `DamageEventSystem` instances:
   - `GatherDamageGroup` - Gather damage sources (fall, suffocation, etc.)
   - `FilterDamageGroup` - Filter/modify damage (armor reduction, PvP checks)
   - `ApplyDamage` - Actually apply damage and trigger death if health <= 0
   - `InspectDamageGroup` - React to damage (hit indicators, sounds, particles)

4. **Interception Point**: By registering a system in `FilterDamageGroup` with `Order.BEFORE` dependency on `ApplyDamage`, we can modify damage BEFORE it kills the player

### Alternative Approach: OnDeathSystem

We could also create an `OnDeathSystem` that reacts when `DeathComponent` is added:

```kotlin
class DownedOnDeathSystem : OnDeathSystem() {
    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Immediately remove DeathComponent
        commandBuffer.tryRemoveComponent(ref, DeathComponent.getComponentType())

        // Set health to 1
        val statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
        statMap?.setStatValue(DefaultEntityStatTypes.getHealth(), 1.0f)

        // Add DownedComponent
        commandBuffer.addComponent(ref, DownedComponent.getComponentType(), DownedComponent(...))
    }
}
```

However, this is riskier because:
- Other death systems may have already executed
- Death screen might flash briefly
- Harder to prevent death animations

The `DamageEventSystem` approach is cleaner because it prevents death from ever happening.

## Troubleshooting

### Death still occurs

- Check logs to see if system is registered
- Verify system is running before `ApplyDamage`
- Check if damage is being cancelled somewhere else

### Mod won't load

- Check manifest.json format
- Verify HytaleServer.jar is in libs/
- Check for compilation errors in build output

### Player gets stuck at 1 HP

- This is expected! Downed state keeps player at 1 HP
- Need to implement timer system to execute death after timeout
- Need to implement revive system to restore health
