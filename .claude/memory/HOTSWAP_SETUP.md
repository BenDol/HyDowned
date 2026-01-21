# IntelliJ Hot Swap Setup for HyDowned

This enables **true hot-reload** for many code changes without server restart!

## Quick Start (Super Simple!)

**In IntelliJ terminal:**
```bash
./gradlew startDevServerDebug
```

**When you see this line in the output:**
```
Listening for transport dt_socket at address: 5005
```

**IntelliJ will show a blue clickable link: "Attach debugger"**
- Click it! That's it - debugger auto-attaches.

**Now you're ready:**
- Edit code → Ctrl+Shift+F9 → Changes live in 1-2 seconds! 🚀

---

## Manual Setup (if auto-attach doesn't work)

### How It Works
- Server runs with debug port 5005 open
- IntelliJ connects to the debug port
- When you edit code, IntelliJ can reload changed classes directly into the running JVM
- Works for method body changes (limitations listed below)

## Setup Instructions

### 1. Create Remote Debug Configuration in IntelliJ

1. Click **Run > Edit Configurations...**
2. Click **+** (Add New Configuration)
3. Select **Remote JVM Debug**
4. Name it: `Hytale Server Debug`
5. Set these values:
   - **Debugger mode**: `Attach to remote JVM`
   - **Host**: `localhost`
   - **Port**: `5005`
   - **Use module classpath**: `HyDowned.main`
6. Click **OK**

### 2. Start Server with Debug Mode

```bash
./gradlew startDevServer
```

You should see:
```
Listening for transport dt_socket at address: 5005
```

### 3. Attach IntelliJ Debugger

1. In IntelliJ, select the `Hytale Server Debug` configuration from dropdown
2. Click the **Debug** button (bug icon) or press **Shift+F9**
3. IntelliJ should connect (shows "Connected" in debug console)

### 4. Make Code Changes with Hot Reload

1. **Edit code** (change method bodies, logic, etc.)
2. **Save** (Ctrl+S)
3. **Run > Debugging Actions > Reload Changed Classes** (or Ctrl+Shift+F9)
4. IntelliJ will hot-swap the classes into the running server
5. **Changes are live immediately!** No restart, no `/plugin reload` needed!

## What Can Be Hot-Swapped

✅ **Works (instant reload):**
- Method body changes (logic, calculations, etc.)
- String constants and messages
- Variable assignments
- Adding/removing lines within existing methods
- Changing conditionals (if/else/when)

❌ **Requires Restart:**
- Adding/removing methods
- Adding/removing fields
- Changing method signatures
- Adding/removing classes
- Changing class hierarchy
- Changes to static initializers

## Workflow Comparison

### Without Hot Swap:
```
Edit code → Build (Ctrl+F9) → Stop server → Start server → Test
⏱️ ~15-20 seconds total
```

### With Hot Swap:
```
Edit code → Reload Changed Classes (Ctrl+Shift+F9) → Test
⏱️ ~1-2 seconds total
```

## Tips

- **Set breakpoints** while debugging to inspect code execution
- **Watch variables** to see state in real-time
- **Evaluate expressions** to test code snippets on the fly
- For major refactors, still do a full restart to be safe

## Troubleshooting

**"Unable to connect"**
- Make sure server is running and shows "Listening for transport dt_socket at address: 5005"
- Check if port 5005 is blocked by firewall

**"Hot swap failed"**
- Your change might not be hot-swappable (see limitations above)
- Do a full restart: Stop server → `./gradlew startDevServer`

**"Classes out of sync"**
- Do a full rebuild: `./gradlew clean build`
- Restart server

## Example: Testing Message Changes

1. Start server and attach debugger
2. Take lethal damage (enter downed state)
3. See message: `"DOWNED! 60 seconds until death..."`
4. Edit `DownedTimerSystem.kt` line 88, change message to: `"YOU'RE DOWN! 60s left!"`
5. Press Ctrl+Shift+F9 (Reload Changed Classes)
6. Take lethal damage again
7. **New message appears instantly!** No restart needed!

---

This gives you the best of both worlds:
- Fast iteration for method changes (hot swap)
- Full restart for structural changes (still fast with persistent auth)
