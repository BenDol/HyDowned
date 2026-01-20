#!/bin/bash

convert_file() {
    local file="$1"
    local category="$2"
    
    # Skip if no println
    if ! grep -q "println" "$file"; then
        return
    fi
    
    # Add import if needed
    if ! grep -q "import com.hydowned.util.Log" "$file"; then
        # Find last import and add after it
        sed -i '/^import /{ :loop; n; /^import /b loop; /^$/{ i\import com.hydowned.util.Log
        b
        } }' "$file" 2>/dev/null || true
    fi
    
    # Convert separator lines
    sed -i "s/println(\"\[HyDowned\] ============================================\")/Log.separator(\"$category\")/g" "$file"
    sed -i "s/println(\"============================================\")/Log.separator(\"$category\")/g" "$file"
    
    # Convert error/warning (⚠, ✗)
    sed -i "s/println(\"\[HyDowned\] \[.*\] ⚠ \(.*\)\")/Log.warning(\"$category\", \"\1\")/g" "$file"
    sed -i "s/println(\"\[HyDowned\] ⚠ \(.*\)\")/Log.warning(\"$category\", \"\1\")/g" "$file"
    sed -i "s/println(\"\[HyDowned\] \[.*\] ✗ \(.*\)\")/Log.error(\"$category\", \"\1\")/g" "$file"
    sed -i "s/println(\"\[HyDowned\] ✗ \(.*\)\")/Log.error(\"$category\", \"\1\")/g" "$file"
    
    # Convert success (✓)
    sed -i "s/println(\"\[HyDowned\] \[.*\] ✓ \(.*\)\")/Log.verbose(\"$category\", \"\1\")/g" "$file"
    sed -i "s/println(\"\[HyDowned\] ✓ \(.*\)\")/Log.verbose(\"$category\", \"\1\")/g" "$file"
    
    # Convert [HyDowned] [Category] messages  
    sed -i "s/println(\"\[HyDowned\] \[.*\] \(.*\)\")/Log.verbose(\"$category\", \"\1\")/g" "$file"
    
    # Convert generic [HyDowned] messages
    sed -i "s/println(\"\[HyDowned\] \(.*\)\")/Log.verbose(\"$category\", \"\1\")/g" "$file"
    
    echo "Converted: $file"
}

# Convert each file with its category
convert_file "HyDownedPlugin.kt" "Plugin"
convert_file "commands/GiveUpCommand.kt" "GiveUpCommand"
convert_file "listeners/PlayerReadyEventListener.kt" "PlayerReady"
convert_file "network/DownedPacketInterceptor.kt" "PacketInterceptor"
convert_file "network/DownedStateTracker.kt" "StateTracker"
convert_file "systems/DownedClearEffectsSystem.kt" "ClearEffects"
convert_file "systems/DownedCollisionDisableSystem.kt" "CollisionDisable"
convert_file "systems/DownedDamageImmunitySystem.kt" "DamageImmunity"
convert_file "systems/DownedHealingSuppressionSystem.kt" "HealingSuppression"
convert_file "systems/DownedHudCleanupSystem.kt" "HudCleanup"
convert_file "systems/DownedHudSystem.kt" "HudSystem"
convert_file "systems/DownedInvisibilitySystem.kt" "Invisibility"
convert_file "systems/DownedLoginCleanupSystem.kt" "LoginCleanup"
convert_file "systems/DownedLogoutHandlerSystem.kt" "LogoutHandler"
convert_file "systems/DownedPacketInterceptorSystem.kt" "PacketInterceptor"
convert_file "systems/DownedPhantomBodySystem.kt" "PhantomBody"
convert_file "systems/DownedPlayerScaleSystem.kt" "PlayerScale"
convert_file "systems/DownedRadiusConstraintSystem.kt" "RadiusConstraint"
convert_file "systems/DownedRemoveInteractionsSystem.kt" "RemoveInteractions"
convert_file "systems/PhantomBodyAnimationSystem.kt" "PhantomAnimation"
convert_file "systems/ReviveInteractionSystem.kt" "ReviveInteraction"
convert_file "systems/DownedDisableItemsSystem.kt" "DisableItems"
convert_file "systems/DownedInteractionBlockingSystem.kt" "InteractionBlocking"
convert_file "util/DownedCleanupHelper.kt" "CleanupHelper"
convert_file "util/PendingDeathTracker.kt" "DeathTracker"

echo "Done!"
