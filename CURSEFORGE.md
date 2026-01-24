# HyDowned - Knocked Out State System

Replaces instant death with a knocked out state where your teammates can revive you. When you take fatal damage, you're knocked out instead of dying - giving your team a chance to bring you back.

## What It Does

When you take fatal damage:

*   You're knocked out at low HP (default 1% of max health, configurable)
*   Healing is completely suppressed - you cannot heal while knocked out
*   A 3 minute countdown starts (configurable)
*   You're immune to most damage while knocked out (configurable - see Damage Immunity settings)
*   Teammates can revive you by crouching nearby
*   If the timer runs out, you die normally
*   Alternatively you can use `/giveup` command to respawn immediately

## Modes

The mod has two modes you can switch between in the config:

**PLAYER Mode (Default)**

*   Your body stays where you went down (laying down)
*   Camera moves 5 blocks above, looking down at your body
*   Movement is completely locked (can't move or interact)  
    _Known limitation: the player that is knocked out will see their character animations can be overridden temporarily, this is only visible to the downed player due to client side movement prediction that we can't control via mods right now_

**PHANTOM Mode**

*   A phantom body spawns where you went down
*   You become invisible and can move around (7 block radius, configurable)
*   Teammates revive the phantom body
*   You're teleported back to your body when revived  
    _Known limitation: This can cause client crashes when invisibilityMode is set to INVISIBLE, this is a bug with the client_

## Configuration

Edit `plugins/HyDowned/config.json`:

**Basic Settings**

```
{
  "downedTimerSeconds": 180,        // 3 minutes until death
  "downedHealthPercent": 0.01,      // Health % when knocked out (0.01 = 1% of max HP)
  "reviveTimerSeconds": 10,         // 10 seconds to complete revive
  "reviveHealthPercent": 0.2,       // Revive at 20% health
  "reviveRange": 2.0,               // How close to crouch (blocks)
  "downedMode": "PLAYER"            // PLAYER or PHANTOM
}
```

**Downed Health System:**

*   `downedHealthPercent` controls what health level players are **restored to** when they take fatal damage (damage that would bring health to 0 or below)
*   Default `0.01` = 1% of max HP (player with 100 max HP → restored to 1 HP when knocked out)
*   Minimum value is `0.001` (0.1 HP minimum to prevent rounding issues)
*   Only lethal damage triggers knocked out state - non-lethal damage passes through normally
*   **Example**: Player at 5 HP takes 10 damage (fatal) → damage canceled, health restored to 1 HP, player enters knocked out state
*   **Health buffer for PvP**: Higher percentages (e.g., 10%) give downed players more HP, preventing instant one-shots when `allowedDownedDamage.player = true`
*   **Healing suppression**: Players at downed health cannot heal - all healing attempts are automatically reverted
*   This prevents knocked out players from healing via food, potions, or regeneration effects

**Multiple Revivers**

```
{
  "multipleReviversMode": "SPEEDUP",    // SPEEDUP or FIRST_ONLY
  "reviveSpeedupPerPlayer": 0.5         // Speed boost per extra reviver
}
```

With `SPEEDUP` mode:

*   1 person reviving: 10 seconds
*   2 people reviving: 6.7 seconds
*   3 people reviving: 5 seconds  
    _Note: the revival speed is configurable_

With `FIRST_ONLY` mode:

*   Only the first person can revive
*   Additional players can't help

**Damage Immunity Settings**

```
{
  "allowedDownedDamage": {
    "player": {
      "enabled": false,
      "damageMultiplier": 0.6
    },
    "mob": {
      "enabled": false,
      "damageMultiplier": 0.6
    },
    "environment": {
      "enabled": false,
      "damageMultiplier": 0.6
    },
    "lava": {
      "enabled": true,
      "damageMultiplier": 0.6
    }
  }
}
```

Fine-grained damage control while knocked out:

*   `enabled` - When `true`, this damage type can affect knocked out players
*   `damageMultiplier` - Multiplier applied to damage (default 0.6 = 40% reduction, 1.0 = full damage, 0.5 = 50% reduction)

**Damage types:**

*   `player` - Player damage (PvP melee/projectile attacks)
*   `mob` - Mob damage (hostile mob attacks)
*   `environment` - Environmental damage (fall, drowning, fire, suffocation)
*   `lava` - Lava damage (enabled by default to prevent being stuck in lava)

**Death/Respawn Handling:**

When a knocked out player takes lethal damage from an allowed damage type:

1.  Player dies normally (death screen appears)
2.  Death screen shows player laying down - sleep animation is maintained throughout death screen
3.  On respawn, player stands up normally and all knocked out state is cleaned up
4.  This ensures proper visual consistency - no standing up during death screen

**PHANTOM Mode Settings**

```
{
  "invisibilityMode": "INVISIBLE"    // INVISIBLE (default) or SCALE
}
```

INVISIBLE uses a visibility component to make you invisible. SCALE shrinks you to 0.01% size. Both work, pick whichever looks better.

## Commands

**/giveup** - Instantly die while knocked out if you don't want to wait for the timer

## How To Use

**When You Go Down:**

*   Initial message: "You've been knocked out! Wait for a teammate to revive you by crouching next to you, or use /giveup to respawn."
*   Timer messages appear at 60s, 30s, and 10s remaining
*   For longer timers, you'll see updates every 30 seconds
*   Nearby players (within 256 blocks) are notified of your knockdown
*   Wait for a teammate to come revive you, or use /giveup to respawn immediately

**When Reviving a Teammate:**

*   Get within 2 blocks of their body (horizontal distance, configurable)
*   Crouch and hold it for 10 seconds (configurable)
*   You'll see "Reviving - Xs" countdown messages
*   The knocked out player sees "\[Your name\] reviving - Xs"
*   Don't move away or stop crouching or it cancels
*   Initial message: "Reviving \[player name\] - stay crouched"

**Multiple People Reviving:**

*   With SPEEDUP mode (default), more people = faster revive
*   Speed formula: 1.0 + (extra\_revivers × 0.5) -> 2 people = 1.5x speed, 3 people = 2.0x speed
*   Everyone needs to stay crouching near the body
*   If anyone moves away, they stop helping but others can continue

## Technical Notes

**PLAYER Mode:**

*   Uses multiple systems to override client-side movement prediction
*   Death animation loops every 0.5 seconds to keep you laying down
*   Movement packets are blocked and movement state is forced to "sleeping"
*   Camera is locked 5 blocks above your body looking down
*   All physics and input are disabled server-side
*   Immune to most damage (mob attacks, fall damage, drowning won't kill you)
*   Lava damage always kills to prevent being stuck
*   Player damage can optionally be allowed via config for PvP scenarios

**PHANTOM Mode:**

*   Spawns a visible phantom body with death animation
*   Your real player becomes invisible (or scaled to 0.01%)
*   Movement is allowed within 7 block radius from phantom body
*   Character collision is disabled (can't push/hit other players)
*   Block collision remains enabled (can't walk through walls)
*   Physical imperceptibility reduces mob targeting (though not guaranteed)
*   Same damage immunity rules as PLAYER mode apply (lava kills, optional PvP damage)

**Logout Behavior:**

*   **Intentional logout**: You die and respawn when you log back in
*   **Crash/disconnect/server shutdown**: Your knocked out state is preserved and restored when you log back in (with remaining timer)
*   State persists across server restarts using file-based tracking
*   Restoration includes your timer position and downed location

**Mob Targeting (Known Limitations):**

**PLAYER Mode**: Mobs will continue to target and attack downed players

*   The Hytale modding API does not expose mob AI or targeting systems
*   No components exist for controlling mob aggro or targeting behavior
*   Intangible and RespondToHit removal only prevent collision/knockback, not AI targeting
*   Invisibility components would prevent targeting but cause client crashes in PLAYER mode
*   Mob damage is disabled by default but can be enabled via config with damage multipliers
*   Note: Player damage can also be enabled via config for PvP scenarios

**PHANTOM Mode with INVISIBLE/SCALE**: More effective at preventing mob targeting

*   Player becomes physically imperceptible (invisible or 0.01% scale)
*   Mobs generally cannot target what they cannot perceive
*   Not guaranteed to work for all mob types due to API limitations

**Bottom Line**: If mob targeting is critical for your use case, use PHANTOM mode. PLAYER mode cannot reliably prevent mob aggro due to Hytale API limitations.

**Damage Immunity Details:**

*   Knocked out players are immune to most damage sources by default
*   Lava damage is enabled by default (prevents being stuck in lava indefinitely)
*   Each damage type can be individually enabled with configurable damage reduction:
    *   Player damage (PvP) via `allowedDownedDamage.player.enabled` with damage multiplier
    *   Mob damage via `allowedDownedDamage.mob.enabled` with damage multiplier
    *   Environmental damage via `allowedDownedDamage.environment.enabled` with damage multiplier
    *   Lava damage via `allowedDownedDamage.lava.enabled` with damage multiplier
*   Default damage multiplier is 0.6 (40% damage reduction) for all enabled damage types
*   Set `damageMultiplier` to 1.0 for full damage, 0.5 for 50% reduction, etc.
*   Timer expiry damage is always allowed (for timeout death)

**Other Notes:**

*   Uses chat messages for feedback (action bar not yet implemented)
*   Death interception works by modifying damage to leave you at configured downed health (default 1% of max HP)
*   Healing suppression actively reverts any healing attempts while knocked out
*   Nearby players (within 256 blocks) are notified when someone goes down

## Installation

**Server:**

1.  Download the JAR
2.  Place in your server's mods folder
3.  Restart server
4.  Config file generates in `plugins/HyDowned/config.json`

**Single Player / Local:**

1.  Download the JAR
2.  Place in `Saves/<YourWorld>/mods/`
3.  Restart the world
4.  Config file generates in `plugins/HyDowned/config.json`

## Requirements

*   Hytale Early Access or later
*   Server-side mod (doesn't need to be on client)

## Translations & Languages

HyDowned includes built-in translations for multiple languages. All player-facing messages automatically display in the player's client language.

**Included Languages:**

*   **English (en-US)** - Default
*   **Portuguese (pt-BR)** - Brazilian Portuguese
*   **German (de-DE)** - German
*   **Korean (ko-KR)** - Korean
*   **Japanese (ja-JP)** - Japanese

**How It Works:**

The mod automatically detects each player's language preference from their Hytale client and shows messages in their language. No configuration needed!

**Adding Your Own Translations:**

If you want to add a new language or customize existing translations, you can modify the JAR file:

1.  Extract the JAR: `jar xf HyDowned-1.1.2.jar`
2.  Navigate to `Server/Languages/`
3.  Copy an existing language folder (e.g., `en-US`) and rename it to your language code (e.g., `es-ES` for Spanish)
4.  Edit the `hydowned.lang` file inside with your translations
5.  Repack the JAR: `jar cf HyDowned-1.1.2.jar *`

**Translation File Format:**

Language files use a simple `key = value` format:

```
# Comments start with #
state.knocked_out = You've been knocked out! Wait for a teammate to revive you by crouching next to you, or use /giveup to respawn.
state.player_knocked_out = {playerName} is knocked out - crouch near their body to revive
timer.remaining_short = {time}s remaining
revive.starting = Reviving {playerName} - stay crouched
command.not_knocked_out = You are not knocked out!
```

See the `en-US/hydowned.lang` file for all the keys available to translate.

**Parameters:**

Translation strings support dynamic parameters using `{parameterName}` syntax:

*   `{playerName}` - Player's display name
*   `{reviverName}` - Name of player doing the reviving
*   `{time}` - Time remaining in seconds
*   `{count}` - Number of players (for pluralization)
*   `{percent}` - Percentage value
*   `{progressBar}` - Visual progress bar (generated by code)

**Contributing Translations:**

If you create translations for a new language, consider contributing them back to the project on GitHub! This helps the entire community.

## Source Code

Available on GitHub: [https://github.com/BenDol/HyDowned](https://github.com/BenDol/HyDowned)

## Support

Report bugs on the [GitHub issues](https://github.com/BenDol/HyDowned/issues) page.

## Credits

Alex for the new logo (I made him work for free)

## License

MIT License