# Development Workflow

## ⚡ Quick Start

```bash
# One command to do everything:
./gradlew build
```

This single command:
1. ✅ Downloads Java 21 (first time only)
2. ✅ Imports latest Hytale Server JAR (if changed)
3. ✅ Compiles your mod
4. ✅ Packages into JAR
5. ✅ **Deploys to Hytale mods folder automatically!**

You're ready to launch Hytale and test!

---

## 🔄 Complete Build Pipeline

### Automatic Tasks (Run with `./gradlew build`)

```
┌─────────────────────────────────────────────────────────┐
│                    Build Pipeline                        │
└─────────────────────────────────────────────────────────┘

1. Java 21 Auto-Provision
   └─> Downloads Azul Zulu JDK 21 (first time only)
       Location: ~/.gradle/jdks/

2. Import Hytale Server JAR (importHytaleServer)
   └─> Checks: %APPDATA%\Hytale\...\Server\HytaleServer.jar
       Copies to: libs/HytaleServer.jar (if changed)
       Status: "Hytale Server JAR is up to date"

3. Compile Kotlin Code
   └─> Uses Java 21 toolchain
       Source: src/main/kotlin/
       Warnings: Unused parameters (expected for template)

4. Process Resources
   └─> Copies: manifest.json, config.json

5. Package JAR (jar)
   └─> Creates: build/libs/HyDowned-1.0.0.jar (1.7 MB)
       Includes: All dependencies bundled

6. Deploy to Hytale (deployToHytale)
   └─> Creates: %APPDATA%\Hytale\UserData\Mods\
       Removes: Old version (if exists)
       Copies: HyDowned-1.0.0.jar
       Output: "✓ Mod deployed successfully!"

BUILD SUCCESSFUL in ~28s
```

---

## 🎯 Common Workflows

### First-Time Setup

```bash
# Clone/create project
cd HyDowned

# Build (downloads Java 21 automatically)
./gradlew build

# Result: Mod is in Hytale mods folder!
```

### Daily Development Loop

```bash
# 1. Make changes to code
vim src/main/kotlin/com/hydowned/...

# 2. Build and deploy
./gradlew build

# 3. Launch Hytale and test
# Mod is already in UserData/Mods!

# 4. Repeat
```

### Quick Iteration

```bash
# Build without cleaning (faster)
./gradlew build

# Build from scratch
./gradlew clean build

# Build without deploying (testing only)
./gradlew build -x deployToHytale
```

### Manual Tasks

```bash
# Update Hytale Server JAR only
./gradlew importHytaleServer

# Deploy without rebuilding
./gradlew deployToHytale

# Check Java toolchains
./gradlew javaToolchains

# List all available tasks
./gradlew tasks
```

---

## 📁 File Locations

### Source Files
```
C:\Projects\games\Hytale\mods\HyDowned\
├── src/main/kotlin/          # Your code
├── src/main/resources/        # Config files
└── build/libs/                # Built JAR (output)
```

### Build Artifacts
```
%USERPROFILE%\.gradle\
├── jdks/                      # Auto-downloaded Java 21
└── caches/                    # Gradle cache

C:\Projects\games\Hytale\mods\HyDowned\
├── libs/
│   └── HytaleServer.jar       # Imported Hytale API
└── build/
    └── libs/
        └── HyDowned-1.0.0.jar # Your built mod
```

### Deployed Mod
```
%APPDATA%\Roaming\Hytale\
└── UserData\
    └── Mods\
        └── HyDowned-1.0.0.jar # Automatically deployed!
```

---

## 🛠️ Advanced Usage

### Skip Specific Tasks

```bash
# Build without importing server JAR
./gradlew build -x importHytaleServer

# Build without deploying
./gradlew build -x deployToHytale

# Build without both
./gradlew build -x importHytaleServer -x deployToHytale
```

### Force Refresh

```bash
# Force re-import of Hytale Server JAR
./gradlew importHytaleServer --rerun-tasks

# Force rebuild everything
./gradlew clean build --no-build-cache
```

### Verbose Output

```bash
# See detailed build information
./gradlew build --info

# See debug information
./gradlew build --debug

# See what would run (dry run)
./gradlew build --dry-run
```

### Parallel Builds

```bash
# Use more threads for faster builds
./gradlew build --parallel --max-workers=4
```

---

## 🚀 Performance Tips

### First Build
- **Time**: ~1-2 minutes
- **Downloads**: Java 21 (~200 MB), dependencies
- **Result**: Everything cached for future builds

### Subsequent Builds
- **Time**: ~5-10 seconds (incremental)
- **Downloads**: None (cached)
- **Result**: Only changed files recompiled

### Optimization
```bash
# Enable Gradle daemon (stays running)
# Already enabled by default!

# Check daemon status
./gradlew --status

# Stop daemon (if needed)
./gradlew --stop
```

---

## 🐛 Troubleshooting

### Build Fails

```bash
# Clean and retry
./gradlew clean build

# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Stop daemon and retry
./gradlew --stop
./gradlew build
```

### Deployment Fails

```bash
# Check if Hytale is running (close it)
# Check permissions
# Manually verify path exists:
explorer "%APPDATA%\Hytale\UserData\Mods"
```

### Import Fails

```bash
# Check Hytale is installed
explorer "%APPDATA%\Hytale\install\release"

# Manually place JAR
# Download from Hytale installation
# Place in: libs/HytaleServer.jar
```

---

## 📊 Build Output Example

```
$ ./gradlew build

> Task :importHytaleServer
Hytale Server JAR is up to date

> Task :compileKotlin
w: file:///.../PlayerDeathListener.kt:17:17 Parameter 'event' is never used
   (23 warnings total - expected for template implementation)

> Task :jar

> Task :deployToHytale
Removed old version
============================================================
✓ Mod deployed successfully!
============================================================
  Source: C:\Projects\...\build\libs\HyDowned-1.0.0.jar
  Destination: C:\Users\...\AppData\...\Mods\HyDowned-1.0.0.jar
  Size: 1.68 MB
============================================================
  Ready to use in Hytale!
  Location: %APPDATA%\Hytale\UserData\Mods
============================================================

BUILD SUCCESSFUL in 28s
6 actionable tasks: 5 executed, 1 up-to-date
```

---

## ✅ Quality of Life Features

- ✨ **Zero Configuration**: Works out of the box
- ⚡ **Fast Iteration**: Build and deploy in one command
- 🔄 **Auto-Update**: Detects changed Hytale Server JAR
- 🧹 **Auto-Cleanup**: Removes old mod versions
- 🎯 **Smart Caching**: Only rebuilds what changed
- 🛡️ **Safe**: Creates directories, handles errors
- 📝 **Clear Output**: Shows exactly what happened

---

## 🎓 Learning More

- `./gradlew tasks` - List all available tasks
- `./gradlew help` - Gradle help
- `./gradlew build --scan` - Detailed build analysis
- See `GRADLE_TASKS.md` for detailed task documentation
- See `BUILD_SETUP.md` for setup details
