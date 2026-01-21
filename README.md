# HyDowned

A downed state system for Hytale that replaces instant death with a revivable state. When a player would normally die, they enter a downed state where teammates can revive them before a timer expires.

## How It Works

When a player takes fatal damage:
1. Death is intercepted and replaced with a downed state
2. Player is set to 1 HP and given damage immunity
3. Player is made intangible - mobs lose aggro and cannot target them
4. A countdown timer starts (default 180 seconds)
5. Teammates can revive by crouching near the downed player
6. If the timer expires, death is executed normally
7. If player intentionally logs out, death is executed on login
8. If player crashes/disconnects, downed state is restored on login (with timer)

The mod uses an ECS (Entity Component System) architecture with systems that manage different aspects of the downed state.

## Modes

### PLAYER Mode (Default)

The player's body stays in place and lays down. This is the simpler approach but has some client-side challenges.

**Technical Details:**

The client runs movement prediction locally, meaning it constantly tries to update the player's movement state based on input. This causes problems when we want the player to stay laying down:

- Client predicts movement → sends `ClientMovement` packets
- Server has to block these packets entirely
- Client's animation system defaults back to idle/walking when animations end
- Movement state constantly resets to non-sleeping

**Solution:**

We use multiple layers to force the player to stay laying down:

1. **Packet Interception** - Block all `ClientMovement` packets from the client
2. **Movement State Override** - Send `sleeping=true` state updates every tick
3. **Animation Loop** - Re-send the Death animation every 0.5s to prevent reversion to idle
4. **Input Suppression** - Filter PlayerInput queue to remove movement commands
5. **Sleep State Sync** - Force movement states to sleeping after Hytale's movement systems run

This creates a constant "battle" with the client's prediction logic, but keeps the player locked in the downed animation from their own perspective.

**Camera:**
- Camera moves to a position above and behind the player looking down
- Uses ServerCameraSettings to control client camera

### PHANTOM Mode

Spawns a phantom body (NPC) at the downed location while the player becomes invisible and can move within a 7 block radius (teleported back to 5 blocks if exceeded).

**Technical Details:**

- Player becomes invisible (SCALE mode shrinks to 0.01% or INVISIBLE mode uses HiddenFromAdventurePlayers)
- Phantom body is a spawned NPC entity with the player's equipment and skin
- Player collision is disabled for character-to-character but blocks/world collision remains
- Player is teleported back to phantom body location on revive

No client-side prediction issues since player movement isn't being suppressed.

## Configuration

Config file: `plugins/HyDowned/config.json`

### Core Settings

```json
{
  "downedTimerSeconds": 180,        // How long until downed player dies
  "reviveTimerSeconds": 10,         // How long to revive a downed player
  "reviveHealthPercent": 0.2,       // Health % restored on revive
  "reviveRange": 2.0,               // Distance to start revive
  "downedMode": "PLAYER"            // PLAYER or PHANTOM
}
```

### Revive Settings

```json
{
  "multipleReviversMode": "SPEEDUP",    // SPEEDUP or FIRST_ONLY
  "reviveSpeedupPerPlayer": 0.5         // Speed multiplier per additional reviver
}
```

**How it works:**
- `SPEEDUP` mode: Each additional reviver speeds up the process
  - 1 reviver: 10s (1.0x speed)
  - 2 revivers: 6.67s (1.5x speed with 0.5 multiplier)
  - 3 revivers: 5s (2.0x speed)
- `FIRST_ONLY` mode: Only first reviver counts, others are blocked

### Visual/Audio Settings (NOT IMPLEMENTED)

```json
{
  "enableParticles": true,     // NOT IMPLEMENTED - FeedbackManager has placeholders
  "enableSounds": true,        // NOT IMPLEMENTED - FeedbackManager has placeholders
  "enableActionBar": true      // NOT IMPLEMENTED - FeedbackManager has placeholders
}
```

These exist in config and FeedbackManager checks them, but FeedbackManager itself is dead code with TODO placeholders. The actual systems (DownedTimerSystem, ReviveInteractionSystem) send direct chat messages instead.

### Movement Settings (NOT IMPLEMENTED)

```json
{
  "downedSpeedMultiplier": 0.1   // NOT IMPLEMENTED - config field exists but unused
}
```

Was planned for PHANTOM mode to slow down the invisible player. Config field exists but no system uses it.

### PHANTOM Mode Settings

```json
{
  "invisibilityMode": "SCALE"    // SCALE or INVISIBLE
}
```

- `SCALE`: Shrinks player to 0.01% size (EntityScaleComponent)
- `INVISIBLE`: Uses HiddenFromAdventurePlayers component

### Logging Configuration

Logging is controlled by the server's `config.json` file, not the mod's config.

**Location:** `dev-server/config.json` (or your server directory)

**Add logging for HyDowned:**
```json
{
  "LogLevels": {
    "com.hydowned": "FINER"
  }
}
```

**Available log levels** (from most to least verbose):
- `FINEST` - Most detailed (not used by HyDowned)
- `FINER` - Very detailed (verbose logs - includes invisibility system, phantom body details)
- `FINE` - Detailed (debug logs - health checks, damage calculations)
- `INFO` - Standard messages (system registration, player state changes)
- `WARNING` - Warnings only
- `SEVERE` - Errors only

**Recommended levels:**
- Development: `FINER` - See all verbose logs including system operations
- Production: `INFO` - Standard operational messages only
- Debugging issues: `FINE` - Detailed debugging without overwhelming output

**Example server config.json:**
```json
{
  "Version": 3,
  "ServerName": "Hytale Server",
  "LogLevels": {
    "com.hydowned": "FINER"
  },
  "Mods": {}
}
```

**Note:** Changes to server `config.json` require a server restart to take effect.

## Commands

### /giveup

Instantly die while downed. Useful if you don't want to wait for the timer or teammates aren't around.

## Architecture Notes

### Systems

The mod uses multiple specialized systems:

**Shared (both modes):**
- `DownedDeathInterceptor` - Catches fatal damage and enters downed state
- `DownedTimerSystem` - Manages countdown timer and chat notifications
- `ReviveInteractionSystem` - Handles crouch-to-revive logic
- `DownedDamageImmunitySystem` - Blocks damage while downed
- `DownedHealingSuppressionSystem` - Blocks healing while downed
- `DownedMobAggroSystem` - Makes player intangible to prevent mob targeting/aggro
- `DownedPacketInterceptorSystem` - Intercepts and modifies packets per player

**PLAYER mode only:**
- `DownedPlayerModeSystem` - Sets player to sleeping state
- `DownedPlayerModeSyncSystem` - Maintains sleeping state every tick
- `DownedAnimationLoopSystem` - Re-sends Death animation every 0.5s
- `DownedMovementSuppressionSystem` - Filters movement from PlayerInput queue
- `DownedMovementStateOverrideSystem` - Sends sleeping=true EntityUpdates every tick
- `DownedCameraSystem` - Controls camera position

**PHANTOM mode only:**
- `DownedPhantomBodySystem` - Spawns and manages phantom NPC
- `PhantomBodyAnimationSystem` - Applies Death animation to phantom
- `DownedCollisionDisableSystem` - Disables character collision
- `DownedRadiusConstraintSystem` - Keeps player within 7 blocks of body (teleports to 5 blocks if exceeded)
- `DownedPlayerScaleSystem` or `DownedInvisibilitySystem` - Makes player invisible

### Component Types

- `DownedComponent` - Main state component attached to downed players
- `PhantomBodyMarker` - Temporary marker on phantom bodies for deferred initialization

### State Management

- `DownedStateTracker` - Thread-safe tracker for network threads (packet handlers)
- `PendingDeathTracker` - File-based tracking for intentional logout (death on login) vs crash/disconnect (restore state on login)
- `DownedCleanupHelper` - Centralized cleanup for death/revive operations

### Packet Handling

The mod uses Netty channel handlers to intercept packets:

- **Incoming**: `DownedPacketInterceptor` wraps incoming packet handlers to block client input
- **Outgoing**: `DownedPacketChannelHandler` inserts into Netty pipeline to modify EntityUpdates

This is necessary because Hytale's server-side systems don't have direct control over client prediction.

## Development

### Hot Reload Workflow

1. Start server: `./gradlew startDevServerDebug`
2. Attach debugger when IntelliJ shows the blue "Attach debugger" link
3. Edit code (method bodies only)
4. Press Ctrl+Shift+F9 to reload changed classes
5. Changes apply in 1-2 seconds without restart

For structural changes (new methods/classes), restart the server through the Hytale client.

### Building

```bash
./gradlew build
```

Auto-deploys to `Saves/<WorldName>/mods/` folders.

## Known Issues

### PLAYER Mode

- Camera position sometimes desyncs if player was moving when downed
- Death animation occasionally gets stuck after respawn (commenting out animation code in cleanup prevents this)
- Very slight delay between entering downed state and camera movement

### PHANTOM Mode

- Phantom body equipment sometimes doesn't show immediately (deferred via PhantomBodyMarker to next tick)
- Player can still see their own nameplate briefly when going invisible
- Radius constraint teleport can feel jarring

### General

- Downed state persists across server restarts and crashes via file-based PendingDeathTracker
- Intentional logout results in death on rejoin, crashes/disconnects restore downed state on rejoin
- Config reload via plugin command not implemented
- No integration with other death-related mods

## License

MIT
