# Gradle Tasks Reference

## Custom Task: importHytaleServer

Automatically imports the Hytale Server JAR from your installation directory.

### Usage

```bash
# Run manually
./gradlew importHytaleServer

# Runs automatically during build
./gradlew build
```

### How It Works

1. **Locates the JAR**: Searches in `%APPDATA%\Roaming\Hytale\install\release\package\game\latest\Server`
2. **Checks for Changes**: Compares timestamp and file size with existing `libs/HytaleServer.jar`
3. **Copies if Needed**: Only copies when:
   - JAR doesn't exist in `libs/`
   - Source JAR is newer than destination
   - File sizes differ
4. **Skips if Up-to-Date**: Doesn't copy unnecessarily

### Output Examples

#### First Run (or after update)
```
> Task :importHytaleServer
Hytale Server JAR not found in libs, copying...
Successfully imported: HytaleServer.jar
  Source: C:\Users\dolb9\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar
  Destination: C:\Projects\games\Hytale\mods\HyDowned\libs\HytaleServer.jar
  Size: 79.77 MB

BUILD SUCCESSFUL in 1s
```

#### Subsequent Runs (no changes)
```
> Task :importHytaleServer
Hytale Server JAR is up to date

BUILD SUCCESSFUL in 676ms
```

### Automatic Execution

The task automatically runs before:
- `compileJava`
- `compileKotlin`
- `build`
- Any task that depends on compilation

This ensures you always have the latest Hytale Server API when building.

### Troubleshooting

#### Warning: "APPDATA environment variable not found"
- **Cause**: Running on non-Windows system or APPDATA not set
- **Solution**: Set APPDATA environment variable or manually place JAR in `libs/HytaleServer.jar`

#### Warning: "Hytale Server JAR not found"
- **Cause**: Hytale not installed or installed in non-standard location
- **Solution**:
  - Install Hytale at default location
  - Or manually copy HytaleServer.jar to `libs/` folder

#### Task skipped but JAR doesn't exist
- **Cause**: `libs/HytaleServer.jar` was manually deleted
- **Solution**: Run `./gradlew importHytaleServer --rerun-tasks`

### Manual Override

If you need to use a different JAR location, you can:

1. **Option 1**: Manually place JAR in `libs/HytaleServer.jar`
2. **Option 2**: Modify `build.gradle` line 57 to point to your custom location:
   ```groovy
   def sourceDir = file("path/to/your/custom/location")
   ```

### Integration with Build Process

The task is integrated via dependency declarations:

```groovy
tasks.withType(JavaCompile) {
    dependsOn importHytaleServer
}
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile) {
    dependsOn importHytaleServer
}
```

This ensures the JAR is always available before compilation starts.

## Custom Task: deployToHytale

Automatically deploys the built mod JAR to your Hytale UserData/Mods folder.

### Usage

```bash
# Runs automatically after build
./gradlew build

# Run manually (doesn't rebuild)
./gradlew deployToHytale
```

### How It Works

1. **Finds the Built JAR**: Locates `build/libs/HyDowned-1.0.0.jar`
2. **Gets Mods Folder**: Uses `%APPDATA%\Roaming\Hytale\UserData\Mods`
3. **Creates Directory**: Creates mods folder if it doesn't exist
4. **Removes Old Version**: Deletes existing JAR with same name
5. **Copies New Version**: Copies the freshly built JAR

### Output Example

```
> Task :deployToHytale
Removed old version
============================================================
✓ Mod deployed successfully!
============================================================
  Source: C:\Projects\...\build\libs\HyDowned-1.0.0.jar
  Destination: C:\Users\...\AppData\Roaming\Hytale\UserData\Mods\HyDowned-1.0.0.jar
  Size: 1.68 MB
============================================================
  Ready to use in Hytale!
  Location: %APPDATA%\Hytale\UserData\Mods
============================================================

BUILD SUCCESSFUL
```

### Automatic Execution

The task automatically runs after `build` completes:

```groovy
build.finalizedBy deployToHytale
```

This means every successful build automatically deploys your mod!

### Benefits

- ✅ No manual copying needed
- ✅ Old version automatically removed
- ✅ Instant testing - just build and launch Hytale
- ✅ Version control - JAR name includes version number
- ✅ Safe - creates directory if missing

### Troubleshooting

#### Warning: "APPDATA environment variable not found"
- **Cause**: Running on non-Windows system
- **Solution**: Manually copy JAR or set APPDATA variable

#### Error: "Built JAR not found"
- **Cause**: JAR wasn't built successfully
- **Solution**: Run `./gradlew build` first

#### Permission denied
- **Cause**: Hytale is running and has the JAR file locked
- **Solution**: Close Hytale before building

### Skip Deployment

If you want to build without deploying:

```bash
./gradlew build -x deployToHytale
```

## Custom Task: checkLogs

Analyzes the latest Hytale log file for errors, warnings, exceptions, and mod-related messages.

### Usage

```bash
# Analyze the latest Hytale log
./gradlew checkLogs

# Or just say "check the logs" during development
```

### How It Works

1. **Finds Latest Log**: Locates most recent `.log` file in `%APPDATA%\Hytale\UserData\Logs`
2. **Analyzes Content**: Scans for:
   - 🔴 **Errors** - Critical issues and failures
   - ⚠️ **Warnings** - Non-critical issues
   - 💥 **Exceptions** - Stack traces and error details
   - 🔧 **Mod Messages** - Plugin/mod loading and HyDowned-specific logs
3. **Displays Summary**: Shows statistics and highlights

### Output Example

```
================================================================================
📋 HYTALE LOG ANALYSIS
================================================================================
Log File: 2026-01-19_11-29-00_client.log
Modified: Mon Jan 19 11:29:55 EST 2026
Size: 43.03 KB
================================================================================

🔴 ERRORS (9):
--------------------------------------------------------------------------------
  2026-01-19 11:29:54 ERROR Failed to boot HytaleServer!
  ... (shows up to 20 errors)

⚠️  WARNINGS (8):
--------------------------------------------------------------------------------
  2026-01-19 11:29:52 WARN Restricted method called
  ... (shows up to 10 warnings)

💥 EXCEPTIONS (6):
--------------------------------------------------------------------------------
  com.hypixel.hytale.codec.exception.CodecException: Failed to decode
  ... (shows up to 3 exceptions with stack traces)

🔧 MOD MESSAGES (89):
--------------------------------------------------------------------------------
  2026-01-19 11:29:54 INFO [PluginManager] Loading pending plugins from directory
  ... (all plugin/mod related messages)

================================================================================
📊 SUMMARY
================================================================================
Total Lines: 337
Errors: 9
Warnings: 8
Exceptions: 6
Mod Messages: 89
================================================================================

💡 TIP: To see the full log, open:
   C:\Users\...\AppData\Roaming\Hytale\UserData\Logs\2026-01-19_11-29-00_client.log
================================================================================
```

### What It Detects

#### Errors
- Lines containing `[ERROR]` or `error:`
- Lines with `failed` (excluding "failed to find")
- Critical failure messages

#### Warnings
- Lines containing `[WARN]` or `warning:`
- Deprecated API usage
- Non-critical issues

#### Exceptions
- Full stack traces with exception type
- Causes and nested exceptions
- Error locations (file:line)

#### Mod Messages
- Plugin loading messages
- HyDowned-specific logs
- Mod enablement/disablement
- Any line mentioning "plugin" or your mod name

### Benefits

- ✅ Quick debugging - see errors without opening files
- ✅ Focused view - filters noise from logs
- ✅ Stack traces - shows full exception details
- ✅ Mod tracking - highlights your mod's messages
- ✅ Historical - analyzes latest log even if Hytale closed

### Common Use Cases

#### After Deploying Mod
```bash
# Build and deploy
./gradlew build

# Launch Hytale and test

# Check what happened
./gradlew checkLogs
```

#### Debugging Crashes
```bash
# Hytale crashed, check why
./gradlew checkLogs

# Look at exceptions section
```

#### Checking Mod Loading
```bash
# Did my mod load?
./gradlew checkLogs

# Check "MOD MESSAGES" section
```

### Troubleshooting

#### Warning: "Hytale logs directory not found"
- **Cause**: Hytale hasn't been run yet
- **Solution**: Launch Hytale at least once to create logs

#### No log files found
- **Cause**: Logs directory exists but is empty
- **Solution**: Run Hytale to generate logs

#### Old log displayed
- **Cause**: Task shows most recent log by timestamp
- **Solution**: This is correct - it shows the latest log file

## Standard Gradle Tasks

### Build
```bash
./gradlew build
```
Compiles, tests, packages the plugin into a JAR, and **automatically deploys** to Hytale mods folder.

### Clean
```bash
./gradlew clean
```
Removes all build artifacts and generated files.

### Compile Only
```bash
./gradlew compileKotlin
```
Only compiles Kotlin source files without creating JAR.

### Show Tasks
```bash
./gradlew tasks
```
Lists all available Gradle tasks.

### Show Dependencies
```bash
./gradlew dependencies
```
Shows the dependency tree for the project.
