# Respawn Systems - How HyDowned Interacts

## How Hytale's Respawn Works

From analyzing `RespawnSystems.java`:

### Death Flow
1. **Player takes lethal damage** → `DeathComponent` is ADDED
   - Triggers all `OnDeathSystem` classes
   - Shows death screen
   - Drops items
   - Plays death animation

2. **Player clicks "Respawn" button** → `DeathComponent` is REMOVED
   - Triggers all `OnRespawnSystem` classes:
     - `RespawnControllerRespawnSystem` - Teleports player to spawn
     - `ResetStatsRespawnSystem` - Resets health/stamina to full
     - `ClearEntityEffectsRespawnSystem` - Clears all effects
     - `ResetPlayerRespawnSystem` - Updates spawn time
   - Player respawns with full health

### OnRespawnSystem Base Class
```java
public abstract class OnRespawnSystem
extends RefChangeSystem<EntityStore, DeathComponent> {
    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, ...) {
        // Triggered when DeathComponent is removed (respawn)
    }
}
```

## How HyDowned Prevents Respawn Issues

### Scenario 1: Player Enters Downed State

**Normal Death**:
- Health reaches 0
- `DeathComponent` added
- Death screen shows
- Player clicks "Respawn"

**With HyDowned**:
- Health reaches 0
- Our `DownedDeathInterceptor` modifies damage to leave player at 1 HP
- `DownedComponent` added (NOT `DeathComponent`)
- Death screen DOES NOT show
- Player stays alive in downed state

**Result**: Respawn systems never trigger because `DeathComponent` was never added ✓

### Scenario 2: Downed Timer Expires (Player Dies)

When the downed timer reaches 0, we need to execute actual death:

```kotlin
fun executeDeathAfterTimeout(ref: Ref<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
    // Remove downed component
    commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

    // Create fatal damage that will kill the player
    val killDamage = Damage(
        Damage.NULL_SOURCE,
        DamageCause.GENERIC, // Or keep original death cause
        999999.0f // Massive damage to ensure death
    )

    // Execute the damage - this will add DeathComponent
    DamageSystems.executeDamage(ref, commandBuffer, killDamage)

    // Now normal death flow happens:
    // 1. DeathComponent added
    // 2. Death screen shows
    // 3. Player can click "Respawn"
    // 4. DeathComponent removed
    // 5. Respawn systems trigger normally
}
```

**Alternative (More Direct)**:
```kotlin
fun executeDeathAfterTimeout(ref: Ref<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
    // Remove downed component
    commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

    // Directly add DeathComponent
    val damage = Damage(
        Damage.NULL_SOURCE,
        DamageCause.GENERIC,
        999999.0f
    )
    DeathComponent.tryAddComponent(commandBuffer, ref, damage)

    // Death systems will trigger normally
}
```

### Scenario 3: Player is Revived

When another player revives the downed player:

```kotlin
fun revivePlayer(ref: Ref<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
    // Remove downed component
    commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

    // Restore health (e.g., 20% of max)
    val entityStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
    if (entityStatMap != null) {
        val healthStat = entityStatMap.get(DefaultEntityStatTypes.getHealth())
        if (healthStat != null) {
            val restoreAmount = healthStat.max * 0.2f // 20% of max health
            entityStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), restoreAmount)
        }
    }

    // Restore normal movement speed
    // TODO: Remove speed modifiers

    // Restore normal animation
    // TODO: Play stand-up animation

    // Clear downed effects
    // TODO: Remove particles, sound effects, etc.

    // Send success message
    // TODO: Show "You have been revived!" message
}
```

**Important**: We do NOT add/remove `DeathComponent` for revives, so respawn systems don't trigger.

## Critical Considerations

### 1. Don't Block Death Completely

Our interceptor must ONLY block the first lethal hit (entering downed state). Once in downed state, if the player takes more damage, they should die normally:

```kotlin
override fun handle(..., damage: Damage) {
    val ref = archetypeChunk.getReferenceTo(index)

    // Check if already downed
    val isDowned = commandBuffer.getArchetype(ref).contains(DownedComponent.getComponentType())

    if (isDowned) {
        // Player is already downed - let damage through
        // This means a second fatal hit will kill them for real
        return
    }

    // Not downed yet - check if damage would be lethal
    val currentHealth = ...
    if (currentHealth - damage.amount <= 0) {
        // First lethal hit - intercept and enter downed state
        damage.amount = currentHealth - 1.0f
        commandBuffer.addComponent(ref, DownedComponent.getComponentType(), DownedComponent(...))
    }
}
```

### 2. Timer System Must Execute Death

The timer system needs access to `CommandBuffer` to execute death:

```kotlin
class DownedTimerTask(
    private val stateManager: DownedStateManager
) : Runnable {
    override fun run() {
        // This won't work - no CommandBuffer access!
        // stateManager.executeDeathForExpired()
    }
}
```

**Solution**: Use an ECS system instead:

```kotlin
class DownedTimerSystem : EntityTickingSystem<EntityStore>() {
    private val query = Query.and(
        DownedComponent.getComponentType(),
        Player.getComponentType()
    )

    override fun tick(dt: Float, index: Int, chunk: ArchetypeChunk<EntityStore>,
                     store: Store<EntityStore>, commandBuffer: CommandBuffer<EntityStore>) {
        val downedComponent = chunk.getComponent(index, DownedComponent.getComponentType())
            ?: return

        // Decrement timer
        downedComponent.downedTimeRemaining -= dt.toInt()

        if (downedComponent.downedTimeRemaining <= 0) {
            // Timer expired - execute death
            val ref = chunk.getReferenceTo(index)
            val killDamage = Damage(Damage.NULL_SOURCE, DamageCause.GENERIC, 999999.0f)

            // Remove downed component first
            commandBuffer.tryRemoveComponent(ref, DownedComponent.getComponentType())

            // Execute death
            DamageSystems.executeDamage(ref, commandBuffer, killDamage)

            println("[HyDowned] Downed timer expired - executing death")
        }
    }
}
```

### 3. Preserve Original Death Cause

Store the original death cause in `DownedComponent`:

```kotlin
class DownedComponent(
    var downedTimeRemaining: Int,
    var reviverPlayerIds: MutableSet<String> = mutableSetOf(),
    var reviveTimeRemaining: Double = 0.0,
    val downedAt: Long = System.currentTimeMillis(),
    var downedLocation: Vector3d? = null,
    var originalDamageCause: DamageCause? = null,  // Store original cause
    var originalDamageInfo: Damage? = null          // Store full damage info
) : Component<EntityStore> {
    // ...
}
```

Then use it when executing death:
```kotlin
val killDamage = Damage(
    originalDamageInfo?.source ?: Damage.NULL_SOURCE,
    downedComponent.originalDamageCause ?: DamageCause.GENERIC,
    999999.0f
)
```

This ensures:
- Kill feed shows correct death cause
- Death message is accurate
- Statistics are recorded correctly

## Implementation Checklist

- [x] Intercept lethal damage with `DownedDeathInterceptor`
- [x] Add `DownedComponent` instead of allowing `DeathComponent`
- [ ] Create `DownedTimerSystem` to tick down downed timer
- [ ] Execute death when timer expires (add `DeathComponent`)
- [ ] Allow downed players to die if they take more damage
- [ ] Implement revive system (remove `DownedComponent`, restore health)
- [ ] Store original damage cause for accurate death messages
- [ ] Ensure respawn systems work normally after downed timer expires

## Summary

**Key Principle**: HyDowned delays death, it doesn't prevent it entirely.

1. **First lethal hit**: Intercepted → Enter downed state (no `DeathComponent`)
2. **During downed state**: Player stays alive at 1 HP
3. **Timer expires OR second lethal hit**: Execute real death (add `DeathComponent`)
4. **Player respawns normally**: Standard respawn systems handle it
5. **Player gets revived**: Remove `DownedComponent`, restore health (no death/respawn)

The respawn systems don't need any modification - they work perfectly as-is because we're just delaying when `DeathComponent` gets added!
