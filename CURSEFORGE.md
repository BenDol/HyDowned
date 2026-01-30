# HyDowned - Downed State System

Replaces instant death with a downed state where your teammates can revive you. When you take fatal damage, you're downed instead of dying - giving your team a chance to bring you back.

## What It Does

When you take fatal damage:

* You enter a downed state at 30% health (configurable)
* A 5-minute countdown starts (configurable)
* You fall into a sleep animation while downed
* Teammates can revive you by crouching nearby for 10 seconds (configurable)
* If the timer runs out, you die normally
* You can hold crouch to give up after 5 seconds
* You can use `/giveup` command to respawn immediately

## Configuration

Edit `plugins/HyDowned/config.json`:

### Downed Settings

```json
{
  "downed": {
    "timerSeconds": 300,           // 5 minutes until death
    "deathOnTimeout": true,        // Die when timer expires (false = auto-revive)
    "healthWhenDowned": 0.30,      // 30% health when downed
    "allowMovement": true,         // Allow horizontal movement while downed
    "applySlow": true,             // Apply slowness effect
    "jumpForce": 0.0,              // Jump force while downed (0 = disabled)
    "aiRetargetRange": 16.0,       // Detection range for nearby players (blocks)
    "allowedDamage": {
      "player": {
        "enabled": true,           // PvP damage enabled
        "damageMultiplier": 0.6    // 40% damage reduction
      },
      "ai": {
        "enabled": true,           // AI/mob damage enabled
        "damageMultiplier": 0.6    // 40% damage reduction
      },
      "environment": {
        "enabled": false,          // Environmental damage disabled
        "damageMultiplier": 0.6
      },
      "lava": {
        "enabled": true,           // Lava kills (prevents being stuck)
        "damageMultiplier": 1.0    // Full lava damage
      }
    }
  }
}
```

**Downed Health:**
* `healthWhenDowned` sets health percentage when entering downed state
* Default `0.30` = 30% of max HP (player with 100 HP → 30 HP when downed)
* Higher percentages provide more survivability against damage while downed
* Lower percentages increase vulnerability

**Movement and Physics:**
* `allowMovement` - Enable/disable horizontal movement while downed
* `applySlow` - Apply slowness effect while downed (stacks with allowMovement=true for crawling)
* `jumpForce` - Jump force while downed (0.0 = no jumping, 11.8 = normal)
* Block breaking and placing is always disabled
* Stamina abilities are always disabled

### Damage Control

Fine-grained control over what can damage downed players:

* `player` - Player-to-player (PvP) damage
* `ai` - AI/mob damage
* `environment` - Fall damage, fire, drowning, suffocation
* `lava` - Lava damage (enabled by default to prevent being stuck)

Each damage type has:
* `enabled` - Whether this damage type affects downed players
* `damageMultiplier` - Damage reduction (0.6 = 40% reduction, 1.0 = full damage)

**AI Targeting Behavior:**
* When AI damage is **enabled**: Downed players are removed only when better targets are available
  * First checks current target list for non-downed players
  * If only downed players in list: scans nearby area (within `aiRetargetRange`) for non-downed players
  * If non-downed players found: removes downed targets to let AI retarget
  * If no better targets exist: keeps downed players as targets
  * Throttled to once per 5 seconds per NPC to prevent spam
* When AI damage is **disabled**: Downed players are removed from AI target lists entirely
* `aiRetargetRange` - Detection radius in blocks for scanning nearby players (default: 16.0)

### Revive Settings

```json
{
  "revive": {
    "timerSeconds": 10,            // 10 seconds to complete revive 
    "maxRange": 2.0,               // Must be within 2 blocks
    "healOnReviveEnabled": false,  // Restore health on revive
    "healOnReviveHealth": 0.30     // Health to restore (30%)
  }
}
```

### Camera Settings

```json
{
  "camera": {
    "enabled": false,              // Enable custom camera
    "defaultCamera": true,         // Use default overhead view 
    "firstPerson": false,          // First-person view
    "positionX": 0.0,              // Camera X offset
    "positionY": 0.8,              // Camera Y offset (height)
    "positionZ": 0.0               // Camera Z offset
  }
}
```

### UI Settings

```json
{
  "ui": {
    "enableSounds": true,          // Sound effects
    "enableProgressBar": true,     // HUD progress bars
    "showChatMessages": false      // Chat notifications 
  }
}
```

* `enableSounds` - Play audio feedback for downed events
* `enableProgressBar` - Show HUD with progress bars and countdowns
* `showChatMessages` - Send chat messages for downed/revive events (HUD is recommended)

## Commands

**`/giveup`** - Instantly die while downed if you don't want to wait for the timer

* Only usable while in downed state
* Bypasses the 5-second give-up countdown
* Immediately triggers death and respawn

**Logout/Reconnect Behavior:**

* Downed state persists across disconnects and server restarts
* `DownedComponent` stores remaining time in entity data
* On reconnect, downed state is restored with remaining time

**Death Handling:**

* When a downed player takes lethal damage from an allowed source, they die normally
* Death screen appears with proper animation maintained
* On respawn, all downed state is cleaned up
* Movement and physics are fully restored

**HUD System:**

* Displays real-time information based on player state
* Different views for downed player vs observers vs revivers

## Translations & Languages

HyDowned includes built-in translations for multiple languages. All player-facing messages automatically display in the player's client language.

**Included Languages:**

* **English (en-US)** - Default
* **Portuguese (pt-BR)** - Brazilian Portuguese
* **German (de-DE)** - German
* **Korean (ko-KR)** - Korean
* **Japanese (ja-JP)** - Japanese

**How It Works:**

The mod automatically detects each player's language preference from their Hytale client and shows messages in their language. No configuration needed!

**Translation Coverage:**

* State notifications (knocked out, revive messages)
* Timer messages (countdown, remaining time)
* Revive interactions (starting, cancelled, completed)
* Commands (descriptions and feedback)
* HUD messages (press to revive, time remaining, giving up)

**Adding Your Own Translations:**

1. Extract the JAR: `jar xf HyDowned.jar`
2. Navigate to `Server/Languages/`
3. Copy an existing language folder (e.g., `en-US`)
4. Rename it to your language code (e.g., `es-ES` for Spanish)
5. Edit the `hydowned.lang` file with your translations
6. Repack the JAR: `jar cf HyDowned.jar *`

**Translation File Format:**

```
# Comments start with #
state.knocked_out = You've been knocked out! Wait for a teammate...
state.player_knocked_out = {playerName} is knocked out
timer.knocked_out_remaining = Knocked out - {time}s remaining
hud.press_to_revive = Press {key} to revive {user}
hud.downed_time = You will die in {time}
```

**Parameters:**

* `{playerName}` / `{user}` - Player's display name
* `{reviverName}` - Name of player doing the reviving
* `{time}` - Time remaining (formatted)
* `{count}` - Number of players
* `{key}` - Keybind reference

See `en-US/hydowned.lang` for all available translation keys.

## Installation

**Server:**

1. Download the JAR file
2. Place in your server's `mods` folder
3. Restart the server
4. Config file generates at `plugins/HyDowned/config.json`
5. Customize config as desired and reload or restart

**Single Player / Local:**

1. Download the JAR file
2. Place in `Saves/<YourWorld>/mods/`
3. Restart the world
4. Config file generates at `plugins/HyDowned/config.json`

## Requirements

* Hytale Early Access or later
* Server-side mod (doesn't need to be on client)
* No dependencies

## Source Code

Available on GitHub: [https://github.com/BenDol/HyDowned](https://github.com/BenDol/HyDowned)

## Support

Report bugs on the [GitHub issues](https://github.com/BenDol/HyDowned/issues) page.

## Credits

* Alex for the logo (I made him work for free)
* Community feedback and testing

## License

MIT License
