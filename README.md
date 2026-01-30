# HyDowned

A downed state system for Hytale that replaces instant death with a revivable state. When a player would normally die, they enter a downed state where teammates can revive them before a timer expires.

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

## License

MIT
