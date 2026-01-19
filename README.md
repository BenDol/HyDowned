# HyDowned - Hytale Downed State Mod

A Hytale plugin that replaces player death with a "downed" state where players can be revived by teammates.

## Features

- **Downed State**: Players enter a downed state instead of dying immediately
- **Configurable Timers**: Customize downed duration (default 3 minutes) and revive time (default 10 seconds)
- **Multiple Revive Modes**:
  - **SPEEDUP**: Multiple players can revive simultaneously, speeding up the process
  - **FIRST_ONLY**: Only one player can revive at a time
- **Configurable Animations**: Choose between laying down or crawling animations
- **Movement Restrictions**: Downed players move at reduced speed (default 10%)
- **Visual Feedback**: Action bars, particle effects, and sounds for immersive experience
- **Health Restoration**: Revived players restore 20% HP by default

## Configuration

Edit `config.json` to customize the mod behavior:

```json
{
  "downedTimerSeconds": 180,          // How long before downed player dies
  "reviveTimerSeconds": 10,           // How long to revive a downed player
  "downedSpeedMultiplier": 0.1,       // Movement speed when downed (10%)
  "reviveHealthPercent": 0.2,         // HP restored on revive (20%)
  "reviveRange": 3.0,                 // Distance to revive (blocks)
  "downedAnimationType": "LAYING",    // LAYING or CRAWLING
  "multipleReviversMode": "SPEEDUP",  // SPEEDUP or FIRST_ONLY
  "reviveSpeedupPerPlayer": 0.5,      // Speed boost per additional reviver
  "enableParticles": true,            // Show particle effects
  "enableSounds": true,               // Play sound effects
  "enableActionBar": true             // Display action bar timers
}
```

## How It Works

### When a Player Dies
1. Death is cancelled and player enters downed state
2. Player animation changes to laying/crawling
3. Movement speed is reduced to 10% (configurable)
4. Downed timer starts counting down from 3 minutes (configurable)
5. Player sees countdown in action bar

### Reviving a Downed Player
1. Another player interacts with the downed player
2. Revive timer starts (10 seconds default)
3. Both players see progress in action bar
4. If revive completes, downed player is restored with 20% HP
5. If downed timer expires first, player dies normally

### Multiple Revivers
- **SPEEDUP Mode**: Each additional reviver speeds up the process
  - 1 reviver: 10 seconds
  - 2 revivers: 6.67 seconds (1.5x speed with 0.5 speedup config)
  - 3 revivers: 5 seconds (2x speed)
- **FIRST_ONLY Mode**: Only first reviver can revive, others are blocked

### Edge Cases
- **Player logs out while downed**: Executes death immediately
- **Reviver moves away**: Revive is cancelled
- **Downed timer expires during revive**: All revivers are notified, player dies
- **Reviver logs out**: Removed from reviver set, others continue

## Project Structure

```
HyDowned/
├── build.gradle                    # Gradle build configuration
├── gradle.properties               # Gradle properties
├── settings.gradle                 # Project settings
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/
│       │       └── hydowned/
│       │           ├── HyDownedPlugin.kt              # Main plugin class
│       │           ├── state/
│       │           │   ├── DownedState.kt             # Player state data
│       │           │   └── DownedStateManager.kt      # State manager
│       │           ├── config/
│       │           │   └── DownedConfig.kt            # Configuration
│       │           ├── listeners/
│       │           │   ├── PlayerDeathListener.kt     # Death events
│       │           │   ├── PlayerInteractListener.kt  # Revive interactions
│       │           │   └── PlayerQuitListener.kt      # Player logout
│       │           ├── timers/
│       │           │   └── DownedTimerTask.kt         # Timer system
│       │           └── util/
│       │               ├── AnimationManager.kt        # Animations
│       │               ├── MovementManager.kt         # Movement speed
│       │               └── FeedbackManager.kt         # Visual/audio feedback
│       └── resources/
│           ├── manifest.json                          # Plugin metadata
│           └── config.json                            # Default config
```

## Building

This project uses Gradle with Kotlin and includes an automatic Hytale Server JAR import task.

### Automatic Hytale Server Import

The build system automatically imports the Hytale Server JAR from your installation:

```bash
# Manually run the import task
./gradlew importHytaleServer
```

The task:
- ✅ Automatically finds the JAR in `%APPDATA%\Roaming\Hytale\install\release\package\game\latest\Server`
- ✅ Only copies if the file has changed (checks timestamp and size)
- ✅ Runs automatically before compilation
- ✅ Creates the `libs/` directory if needed

Output example:
```
> Task :importHytaleServer
Successfully imported: HytaleServer.jar
  Source: C:\Users\...\Hytale\...\Server\HytaleServer.jar
  Destination: C:\Projects\...\HyDowned\libs\HytaleServer.jar
  Size: 79.77 MB
```

### Building the Plugin

```bash
./gradlew build
```

The build process:
1. ✅ `importHytaleServer` - Imports Hytale Server JAR (if needed)
2. ✅ Compiles Kotlin code
3. ✅ Packages into JAR: `build/libs/HyDowned-1.0.0.jar`
4. ✅ `deployToHytale` - **Automatically deploys to Hytale mods folder!**

Output example:
```
> Task :deployToHytale
============================================================
✓ Mod deployed successfully!
============================================================
  Source: ...\build\libs\HyDowned-1.0.0.jar
  Destination: %APPDATA%\Hytale\UserData\Mods\HyDowned-1.0.0.jar
  Size: 1.68 MB
============================================================
  Ready to use in Hytale!
============================================================
```

### Manual Deployment

You can also deploy manually without rebuilding:

```bash
./gradlew deployToHytale
```

## Installation

### Automatic (Recommended)

Simply build the project - the mod is automatically deployed!

```bash
./gradlew build
```

The mod JAR is automatically copied to `%APPDATA%\Hytale\UserData\Mods\HyDowned-1.0.0.jar`

### Manual

1. Build the plugin: `./gradlew build`
2. Find the JAR in `build/libs/HyDowned-1.0.0.jar`
3. Copy to your Hytale mods directory:
   - **Client mods**: `%APPDATA%\Hytale\UserData\Mods`
   - **Server plugins**: Your server's plugins directory
4. Configure `config.json` in the plugin data folder
5. Restart Hytale or reload the plugin

## Requirements

- **No manual Java installation required!** Java 21 is automatically downloaded by Gradle
- Hytale Server (Early Access or later) installed at the default location
- KTale library (optional, commented out by default)

### About Java 21 Auto-Provisioning

This project uses Gradle's toolchain auto-provisioning feature:
- ✅ **Automatically downloads** Java 21 (Azul Zulu) when you first build
- ✅ **Project-specific** - doesn't affect your system Java installation
- ✅ **No configuration needed** - just run `./gradlew build`
- ✅ **Cached** in `~/.gradle/jdks/` for reuse across projects

You can verify installed toolchains with:
```bash
./gradlew javaToolchains
```

## Development Status

**IMPORTANT**: This is a template implementation with placeholder code. The actual Hytale API integration needs to be completed. All source files contain `TODO` comments indicating where real API calls should be implemented.

### What's Implemented
- ✅ Complete project structure
- ✅ Configuration system
- ✅ State management logic
- ✅ Event listener structure
- ✅ Timer system logic
- ✅ Manager classes (Animation, Movement, Feedback)

### What Needs Hytale API
- ⚠️ Player death event handling
- ⚠️ Player interaction events
- ⚠️ Animation API integration
- ⚠️ Movement speed modification
- ⚠️ Particle and sound effects
- ⚠️ Action bar and chat messages
- ⚠️ Health modification
- ⚠️ Location/distance calculations

## Contributing

To integrate with the Hytale API:

1. Search for `TODO` comments in the source code
2. Replace placeholder implementations with actual Hytale API calls
3. Test each feature thoroughly
4. Update this README with actual API usage examples

## License

This project is provided as-is for educational and modding purposes.

## Credits

Built using:
- [Hytale Plugin Template](https://github.com/realBritakee/hytale-template-plugin)
- [KTale](https://github.com/ModLabsCC/ktale) - Kotlin helpers for Hytale
- [Hytale Modding Documentation](https://britakee-studios.gitbook.io/hytale-modding-documentation)
