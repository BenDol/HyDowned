# Death Interception TODO

## Current Status
✅ Plugin loads successfully
✅ Configuration system works
✅ State management implemented
⚠️ Death interception needs Hytale API implementation

## What We Know About Hytale's Architecture

### ECS (Entity Component System)
Hytale uses an Entity Component System rather than traditional event-driven architecture:
- Entities are made up of Components (data)
- Systems process entities with specific components
- Events exist but may not cover all gameplay mechanics

### Death-Related Classes Found
```
com/hypixel/hytale/server/core/modules/entity/damage/DeathSystems.class
├── DeathSystems$OnDeathSystem.class
├── DeathSystems$PlayerDeathMarker.class
├── DeathSystems$PlayerDeathScreen.class
├── DeathSystems$ClearHealth.class
├── Death Systems$DropPlayerDeathItems.class
└── DeathSystems$KillFeed.class

com/hypixel/hytale/server/core/modules/entity/damage/DamageEventSystem.class
com/hypixel/hytale/server/core/modules/entitystats/asset/condition/AliveCondition.class
```

### Available Plugin APIs
From `PluginBase`:
- `getEventRegistry()` - Register event listeners
- `getEntityRegistry()` - Work with entities
- `getEntityStoreRegistry()` - Access ECS components
- `getCommandRegistry()` - Register commands

## Approaches to Implement Death Interception

### Option 1: Event-Based (If Event Exists)
Look for a death-related event we can listen to:
```kotlin
// Pseudocode - needs actual event class
eventRegistry.register(SomeDeathEvent::class.java) { event ->
    if (event.entity is Player) {
        event.cancelled = true
        val player = event.entity as Player
        stateManager.setDowned(player.uniqueId, player.location)
        // Apply downed state...
    }
}
```

**Status**: No `PlayerDeathEvent` found in event package - may not exist

### Option 2: ECS System Hook (More Complex)
Register a custom ECS system that runs before `DeathSystems$OnDeathSystem`:
```kotlin
// Pseudocode - needs actual ECS API
entityStoreRegistry.registerSystem(
    order = SystemOrder.BEFORE_DEATH,
    components = listOf(PlayerComponent::class, HealthComponent::class)
) { entity ->
    val health = entity.get(HealthComponent::class)
    if (health.current <= 0) {
        // Prevent death, set downed state
        health.current = 1.0
        entity.add(DownedStateComponent(downedTime = config.downedTimerSeconds))
    }
}
```

**Status**: Need to understand ECS component registration API

### Option 3: Damage Event Interception (Most Likely)
Hook into damage events before death occurs:
```kotlin
eventRegistry.register(DamageEvent::class.java) { event ->
    if (event.entity is Player) {
        val player = event.entity as Player
        val newHealth = player.health - event.damage

        if (newHealth <= 0 && !stateManager.isDowned(player.uniqueId)) {
            // Cancel fatal damage
            event.cancelled = true

            // Set to 1 HP
            player.health = 1.0

            // Enter downed state
            stateManager.setDowned(player.uniqueId, player.location)
            // Apply animations, speed reduction, etc.
        }
    }
}
```

**Status**: Need to find the actual damage event class

### Option 4: Health Component Monitoring
Monitor health component changes and intercept when reaching zero:
```kotlin
// Register a system that processes health changes
entityStoreRegistry.registerSystem { entity ->
    if (entity.has(PlayerComponent::class) && entity.has(HealthComponent::class)) {
        val health = entity.get(HealthComponent::class)
        val player = entity.get(PlayerComponent::class).player

        if (health.current <= 0 && !stateManager.isDowned(player.uniqueId)) {
            // Prevent death
            health.current = 1.0
            // Enter downed state...
        }
    }
}
```

**Status**: Need component API documentation

## Required Research

1. **Find the actual death trigger**:
   - Decompile `DeathSystems.class` to see how death is triggered
   - Look for health component changes that cause death
   - Check if there's a pre-death event or hook

2. **Understand event registration**:
   - Look at core Hytale modules for examples
   - Find event classes in damage/entity packages
   - Test with simple events like `PlayerInteractEvent`

3. **Access player data**:
   - How to get Player from entity ID
   - How to modify player health
   - How to apply animations and speed modifiers
   - How to send messages/feedback to players

4. **ECS Component Access**:
   - How to query entities by component
   - How to modify component data
   - System execution order and priorities

## Testing Strategy

### Phase 1: Command-Based Testing (Current)
Use `/testdowned` command to manually trigger downed state:
- ✅ Test state management
- ✅ Test timer countdown
- ✅ Test revive mechanics
- ✅ Test configuration system

### Phase 2: Simple Event Hook
Once we understand events, hook into something simple like:
```kotlin
// Test with player interact event first
eventRegistry.register(PlayerInteractEvent::class.java) { event ->
    logger.info("Player interacted: ${event.player.name}")
}
```

### Phase 3: Damage/Death Hook
Implement actual death interception using the approach that works.

### Phase 4: Full Integration
- Animation changes
- Speed modifiers
- Visual feedback
- Revive interactions

## Next Steps

1. **Explore existing Hytale plugins** for examples (if any available)
2. **Decompile key classes** to understand internal mechanics
3. **Test event registration** with simple events
4. **Implement death hook** using working approach
5. **Add polish** (animations, feedback, etc.)

## Temporary Workaround

For immediate testing, use the test command approach:
```
/testdowned - Enter downed state manually
/revivetest <player> - Revive a downed player manually
```

This lets us test all the core mechanics while we figure out the death hook.
