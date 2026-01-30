# HyDowned Implementation Status

**Last Updated**: January 19, 2026

## ✅ Completed Systems

### 1. Death Interception System (`DownedDeathInterceptor`)
**Status**: ✅ **COMPLETE AND READY TO TEST**

**What it does**:
- Intercepts damage that would kill the player
- Modifies damage to leave player at 1 HP
- Adds `DownedComponent` instead of allowing `DeathComponent`
- Stores original damage info for accurate death messages later

**How it works**:
- Runs in `FilterDamageGroup` BEFORE `ApplyDamage`
- Checks if damage would be lethal (health - damage <= 0)
- If lethal and player not already downed:
  - Reduces damage to leave 1 HP
  - Adds `DownedComponent` with timer
  - Logs detailed information

**Edge case handling**:
- If player is already downed, allows damage through (so second hit kills them)
- Preserves original damage source and cause for death messages

**File**: `src/main/kotlin/com/hydowned/systems/DownedDeathInterceptor.kt`

### 2. Downed Timer System (`DownedTimerSystem`)
**Status**: ✅ **COMPLETE AND READY TO TEST**

**What it does**:
- Counts down the downed timer every second
- Executes real death when timer expires
- Logs timer status

**How it works**:
- Runs every 1 second (`DelayedEntitySystem` with 1.0f delay)
- Queries for entities with `DownedComponent`
- Decrements timer by 1 each tick
- When timer reaches 0:
  - Removes `DownedComponent`
  - Creates massive damage (999999) to ensure death
  - Executes damage which adds `DeathComponent`
  - Normal death/respawn flow proceeds

**File**: `src/main/kotlin/com/hydowned/systems/DownedTimerSystem.kt`

### 3. Visual Effects System (`DownedVisualEffectsSystem`)
**Status**: ✅ **STRUCTURE COMPLETE** (placeholders for actual effects)

**What it does**:
- Applies visual effects when entering downed state
- Removes effects when leaving downed state

**Planned effects** (TODOs):
- Downed animation (lying/crawling based on config)
- Movement speed reduction (10% of normal speed)
- Particle effects (red/warning particles if enabled)
- Sound effects (hurt/downed sounds if enabled)
- Action bar messages to player

**File**: `src/main/kotlin/com/hydowned/systems/DownedVisualEffectsSystem.kt`

### 4. ECS Component (`DownedComponent`)
**Status**: ✅ **COMPLETE**

**What it stores**:
- `downedTimeRemaining` - Seconds until death
- `reviverPlayerIds` - Set of UUIDs of players reviving
- `reviveTimeRemaining` - Progress toward revival
- `downedAt` - Timestamp when downed
- `downedLocation` - Location where player was downed
- `originalDamageCause` - Original cause (for death messages)
- `originalDamage` - Full damage object

**File**: `src/main/kotlin/com/hydowned/components/DownedComponent.kt`

### 5. Plugin Setup (`HyDownedPlugin`)
**Status**: ✅ **COMPLETE**

**What it does**:
- Registers `DownedComponent` with ECS
- Registers all systems with proper order
- Loads configuration
- Initializes state manager
- Handles plugin lifecycle

**Systems registered**:
1. `DownedDeathInterceptor` - Intercepts death
2. `DownedTimerSystem` - Counts down timer
3. `DownedVisualEffectsSystem` - Applies effects

**File**: `src/main/kotlin/com/hydowned/HyDownedPlugin.kt`

## ⚠️ TODO: Waiting for Hytale API

These features require Hytale APIs that we don't fully understand yet:

### 1. Player Interaction / Revive System
**Status**: ⚠️ **BLOCKED** - Needs player interaction API

**What it needs**:
- Detect when player right-clicks/interacts with another player
- Check if target is downed
- Start revive progress
- Handle multiple revivers (speed up or first-only mode)

**Implementation approach**:
```kotlin
// Pseudocode - waiting for interaction API
eventRegistry.register(PlayerInteractEvent::class.java) { event ->
    val reviver = event.player
    val target = event.targetEntity

    if (target has DownedComponent) {
        // Add reviver to downedComponent.reviverPlayerIds
        // Start revive timer
    }
}
```

### 2. Animation System
**Status**: ⚠️ **BLOCKED** - Needs animation API

**What it needs**:
- Apply "lying down" or "crawling" animation to downed player
- Restore normal animation when revived/dead

**Implementation approach**:
```kotlin
// Pseudocode - waiting for animation API
AnimationUtils.playAnimation(ref, AnimationSlot.Status, "downed_lying", true, commandBuffer)
```

### 3. Movement Speed Modification
**Status**: ⚠️ **BLOCKED** - Needs movement API

**What it needs**:
- Reduce movement speed to 10% (or config value)
- Restore normal speed when revived/dead

**Implementation approach**:
```kotlin
// Pseudocode - waiting for movement API
movementManager.setSpeedMultiplier(ref, config.downedSpeedMultiplier)
```

### 4. Visual Feedback (Particles, Sounds, Messages)
**Status**: ⚠️ **BLOCKED** - Needs various APIs

**What it needs**:
- Particle effects around downed player
- Sound effects (heartbeat, hurt sounds)
- Action bar messages with timer countdown
- Chat messages for revive/death

### 5. Commands
**Status**: ⚠️ **BLOCKED** - Needs command API

**Commands planned**:
- `/testdowned` - Manually enter downed state for testing
- `/revivetest <player>` - Manually revive a player for testing

**File prepared**: `src/main/kotlin/com/hydowned/commands/TestDownedCommand.kt`

## 🔄 How It Currently Works

### Death Flow (Implemented)

**Normal Death** (without HyDowned):
```
1. Player takes fatal damage
2. Health reaches 0
3. DeathComponent added
4. Death systems trigger (death screen, item drops, etc.)
5. Player clicks "Respawn"
6. DeathComponent removed
7. Respawn systems trigger (teleport, restore health)
```

**With HyDowned** (currently):
```
1. Player takes fatal damage
2. Health would reach 0
3. ⚡ DownedDeathInterceptor catches this
4. Damage reduced to leave 1 HP
5. DownedComponent added (NOT DeathComponent)
6. Player stays alive in downed state
7. Timer counts down (180 seconds default)
8. Visual effects applied (placeholders)
9. After timeout: DeathComponent added → normal death flow
```

### Edge Cases Handled

**Player takes more damage while downed**:
- Interceptor checks if already downed
- If yes, allows damage through → player dies immediately
- Normal death screen and respawn

**Player disconnects while downed**:
- State manager cleanup executes death
- No stuck states on reconnect

**Server restarts while player downed**:
- Plugin shutdown calls `stateManager.cleanup()`
- All downed players execute death

## 📊 Testing Checklist

When you test the mod, verify:

- [ ] Taking fatal damage enters downed state (no death screen)
- [ ] Health is 1 HP while downed
- [ ] Timer counts down in logs (every second)
- [ ] After 180 seconds, player dies normally
- [ ] Death screen appears after timeout
- [ ] Can respawn normally after timeout
- [ ] Taking damage while downed kills immediately
- [ ] Second fatal hit shows death screen
- [ ] Original death cause preserved in death message

**Expected log output**:
```
[HyDowned] ============================================
[HyDowned] Intercepting lethal damage!
[HyDowned]   Current Health: 20.0
[HyDowned]   Damage Amount: 100.0
[HyDowned]   Would be fatal: -80.0 HP
[HyDowned] ============================================
[HyDowned] Modified damage to: 19.0 (leaves player at 1 HP)
[HyDowned] ✓ Player entered downed state
[HyDowned]   Timer: 180 seconds
[HyDowned] ============================================
[HyDowned] Applying visual effects for downed state...
[HyDowned] ✓ Visual effects applied (placeholder)
[HyDowned] Timer tick: 179s remaining
[HyDowned] Timer tick: 178s remaining
...
[HyDowned] Timer tick: 1s remaining
[HyDowned] ============================================
[HyDowned] Downed timer expired!
[HyDowned] Executing death...
[HyDowned] ============================================
[HyDowned] Removing visual effects from downed state...
[HyDowned] ✓ Visual effects removed (placeholder)
[HyDowned] ✓ Death executed, normal respawn flow will proceed
```

## 🎯 Next Steps After Testing

Once core death interception is confirmed working:

1. **Implement revive mechanic** (when interaction API understood)
   - Player right-click interaction
   - Revive progress bar
   - Multiple reviver support

2. **Add visual polish** (when APIs understood)
   - Downed animations
   - Movement speed reduction
   - Particle effects
   - Sound effects

3. **Add commands** (when command API understood)
   - Test commands for manual control
   - Admin commands for force revive/death

4. **Optimize and tune**
   - Timer values
   - Speed multipliers
   - Visual effect intensity

## 📁 File Structure

```
src/main/kotlin/com/hydowned/
├── HyDownedPlugin.kt                    # ✅ Main plugin class
├── components/
│   └── DownedComponent.kt               # ✅ ECS component
├── systems/
│   ├── DownedDeathInterceptor.kt        # ✅ Death interception
│   ├── DownedTimerSystem.kt             # ✅ Timer countdown
│   └── DownedVisualEffectsSystem.kt     # ⚠️ Visual effects (placeholders)
├── config/
│   └── DownedConfig.kt                  # ✅ Configuration
├── state/
│   └── DownedStateManager.kt            # ✅ State management
└── commands/
    └── TestDownedCommand.kt             # ⚠️ Commands (placeholder)
```

## 🔧 Configuration

Default config values:
```json
{
  "downedTimerSeconds": 180,           // 3 minutes
  "reviveTimerSeconds": 10,            // 10 seconds
  "downedSpeedMultiplier": 0.1,        // 10% speed
  "reviveHealthPercent": 0.2,          // 20% HP on revive
  "reviveRange": 3.0,                  // 3 blocks
  "downedAnimationType": "LAYING",     // or "CRAWLING"
  "multipleReviversMode": "SPEEDUP",   // or "FIRST_ONLY"
  "reviveSpeedupPerPlayer": 0.5,       // 50% faster per reviver
  "enableSounds": true,
  "enableActionBar": true
}
```

## 🎮 Build Status

**Latest Build**: ✅ **SUCCESS**
- Compilation: ✅ Complete
- Deployment: ⚠️ Requires Hytale to be closed
- Systems Registered: 3/3 core systems

**Known Warnings**:
- Deprecation warning for `damage.cause` (fixed by using `damageCauseIndex`)

**Build Command**:
```bash
./gradlew build         # Build only
./gradlew deployToHytale # Build and deploy (requires Hytale closed)
```

## 📝 Summary

**Core death interception is COMPLETE and ready to test!**

The mod will successfully:
- Intercept player death
- Keep player alive at 1 HP
- Count down a timer
- Execute real death after timeout
- Work with normal respawn systems

Missing features require Hytale APIs we don't have yet:
- Visual effects (animation, speed, particles)
- Revive interaction
- Commands

**Action Required**: Close Hytale → Deploy → Test the core death interception!
