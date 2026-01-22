# HyDowned - Knocked Out State System

Replaces instant death with a knocked out state where your teammates can revive you. When you take fatal damage, you're knocked out instead of dying - giving your team a chance to bring you back.

## What It Does

When you take fatal damage:

*   You're knocked out at 1 HP instead of dying
*   A 3 minute countdown starts (configurable)
*   You're immune to most damage while knocked out (configurable - see PvP settings)
*   Teammates can revive you by crouching nearby
*   If the timer runs out, you die normally
*   Alternatively you can use `/giveup` command to respawn immediately

## Modes

The mod has two modes you can switch between in the config:

**PLAYER Mode (Default)**

*   Your body stays where you went down (laying down)
*   Camera moves 5 blocks above, looking down at your body
*   Movement is completely locked (can't move or interact)  
    _Known limitation: the player that is knocked out will see their character animations change to idle and move and sometimes spam the death animation, this is only visible to the downed player due to client side movement prediction that we can't control via mods right now_

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
  "reviveTimerSeconds": 10,         // 10 seconds to complete revive
  "reviveHealthPercent": 0.2,       // Revive at 20% health
  "reviveRange": 2.0,               // How close to crouch (blocks)
  "downedMode": "PLAYER"            // PLAYER or PHANTOM
}
```

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

**PvP / Combat Settings**

```
{
  "allowPlayerDamageWhileDowned": false    // Allow players to kill downed enemies
}
```

When `false` (default):

*   Knocked out players are immune to all damage except lava
*   Players cannot be "finished off" by enemies
*   Lava damage always kills (prevents being stuck)

When `true`:

*   Enemy players can kill knocked out players with attacks
*   Applies to both melee and projectile damage
*   Allows interrupting enemy revives in PvP
*   Mob and environmental damage (fall, drowning) still blocked
*   Lava damage always kills (prevents being stuck)

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
*   You remain immune to mob damage, so their attacks won't kill you
*   Note: Player damage can be optionally enabled via config for PvP scenarios

**PHANTOM Mode with INVISIBLE/SCALE**: More effective at preventing mob targeting

*   Player becomes physically imperceptible (invisible or 0.01% scale)
*   Mobs generally cannot target what they cannot perceive
*   Not guaranteed to work for all mob types due to API limitations

**Bottom Line**: If mob targeting is critical for your use case, use PHANTOM mode. PLAYER mode cannot reliably prevent mob aggro due to Hytale API limitations.

**Damage Immunity Details:**

*   Knocked out players are immune to most damage sources
*   Lava damage ALWAYS kills (prevents being stuck in lava indefinitely)
*   Player damage can be optionally enabled via `allowPlayerDamageWhileDowned` config
*   Mob damage is always blocked (mobs can't finish you off)
*   Environmental damage (fall, drowning, fire) is always blocked
*   Timer expiry damage is always allowed (for timeout death)

**Other Notes:**

*   Uses chat messages for feedback (action bar not yet implemented)
*   Death interception works by modifying damage to leave you at 1 HP
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

## Source Code

Available on GitHub: [https://github.com/BenDol/HyDowned](https://github.com/BenDol/HyDowned)

## Support

Report bugs on the [GitHub issues](https://github.com/BenDol/HyDowned/issues) page.

## Credits

Alex for the new logo (I made him work for free)

## License

MIT License