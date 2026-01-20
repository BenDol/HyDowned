# IntelliJ Auto-Attach Setup (One-Time Configuration)

This makes IntelliJ **automatically** attach the debugger when it sees the debug port message - no clicking needed!

## One-Time Setup (Takes 30 seconds)

### Method 1: Enable Console Auto-Attach (Easiest)

1. **Open IntelliJ Settings** (File → Settings or Ctrl+Alt+S)

2. **Navigate to:** Build, Execution, Deployment → Debugger

3. **Find the section:** "Automatic debugger attachment"

4. **Enable:** ☑ "Attach automatically to processes that produce output like 'Listening for transport dt_socket at address: XXXX'"

5. **Click OK**

6. **Done!** Now IntelliJ will auto-attach whenever it sees that message.

---

### Method 2: Use the "Attach to Process" Feature

Alternatively, you can use IntelliJ's built-in "Attach to Process" feature with a keyboard shortcut:

1. Run: `./gradlew startDevServerDebug`

2. Wait for server to show: `Listening for transport dt_socket at address: 5005`

3. Press: **Ctrl+Alt+F5** (Attach to Process)

4. IntelliJ will show a list of running Java processes

5. Select the process listening on port 5005 (will say "transport dt_socket")

6. Press Enter - debugger attaches!

This is still faster than manually selecting the debug config and clicking debug.

---

## Verification

After setup, test it:

1. Run: `./gradlew startDevServerDebug` in IntelliJ terminal

2. When you see: `Listening for transport dt_socket at address: 5005`

3. **IntelliJ should automatically attach** without any clicking!

4. Look for "Connected to the target VM, address: 'localhost:5005'" in the Debug console

---

## If Auto-Attach Doesn't Work

If the auto-attach setting isn't available in your IntelliJ version, use this workaround:

### Create a Compound Run Configuration

1. **Create the remote debug config** (if you haven't):
   - Run → Edit Configurations → + → Remote JVM Debug
   - Name: `Hytale Server Debug`
   - Host: `localhost`, Port: `5005`

2. **Create a custom run configuration**:
   - Run → Edit Configurations → + → Shell Script
   - Name: `Start & Attach Debug`
   - Script text:
     ```bash
     ./gradlew startDevServerDebug &
     sleep 10
     ```
   - Before launch: Add → Run Another Configuration → Select "Hytale Server Debug"

3. **Run the compound configuration** - it will start server then auto-attach!

---

## Your Final Workflow

Once setup:

```bash
./gradlew startDevServerDebug
```

→ **Auto-attaches debugger** (no clicking!)

→ Edit code → Ctrl+Shift+F9 → Changes live in 1-2 seconds!

Perfect hot-swap development! 🚀
