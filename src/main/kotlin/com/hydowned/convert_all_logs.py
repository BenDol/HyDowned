#!/usr/bin/env python3
import os
import re

def get_category_from_file(filename):
    """Extract category name from filename"""
    basename = os.path.basename(filename).replace('.kt', '')
    
    # Map specific files to categories
    category_map = {
        'HyDownedPlugin': 'Plugin',
        'DownedDeathInterceptor': 'DeathInterceptor',
        'DownedTimerSystem': 'TimerSystem',
        'ReviveInteractionSystem': 'ReviveInteraction',
        'DownedCleanupHelper': 'CleanupHelper',
        'DownedLoginCleanupSystem': 'LoginCleanup',
        'DownedLogoutHandlerSystem': 'LogoutHandler',
        'DownedPhantomBodySystem': 'PhantomBody',
        'PhantomBodyAnimationSystem': 'PhantomAnimation',
        'DownedHealingSuppressionSystem': 'HealingSuppression',
        'DownedDamageImmunitySystem': 'DamageImmunity',
        'DownedCollisionDisableSystem': 'CollisionDisable',
        'DownedInvisibilitySystem': 'Invisibility',
        'DownedPlayerScaleSystem': 'PlayerScale',
        'DownedRemoveInteractionsSystem': 'RemoveInteractions',
        'DownedPacketInterceptorSystem': 'PacketInterceptor',
        'DownedDisableItemsSystem': 'DisableItems',
        'DownedInteractionBlockingSystem': 'InteractionBlocking',
        'DownedRadiusConstraintSystem': 'RadiusConstraint',
        'DownedClearEffectsSystem': 'ClearEffects',
        'GiveUpCommand': 'GiveUpCommand',
        'PlayerReadyEventListener': 'PlayerReady',
        'PendingDeathTracker': 'DeathTracker',
        'DownedPacketInterceptor': 'PacketInterceptor',
    }
    
    return category_map.get(basename, basename.replace('Downed', '').replace('System', ''))

def convert_println(line, category):
    """Convert a println statement to Log call"""
    
    # Match println with [HyDowned] prefix
    patterns = [
        # Separator lines
        (r'println\("(\[HyDowned\]|\s*)\s*={40,}"\)', f'Log.separator("{category}")'),
        
        # Error/Warning with ⚠ or ✗
        (r'println\("\[HyDowned\]\s*\[.*?\]\s*[⚠✗]\s*(.+?)"\)', f'Log.warning("{category}", r"\1")'),
        (r'println\("\[HyDowned\]\s*[⚠✗]\s*(.+?)"\)', f'Log.warning("{category}", r"\1")'),
        
        # Success with ✓
        (r'println\("\[HyDowned\]\s*\[.*?\]\s*✓\s*(.+?)"\)', f'Log.verbose("{category}", r"\1")'),
        (r'println\("\[HyDowned\]\s*✓\s*(.+?)"\)', f'Log.verbose("{category}", r"\1")'),
        
        # Info messages with [Category]
        (r'println\("\[HyDowned\]\s*\[(.+?)\]\s*(.+?)"\)', f'Log.info("{category}", r"\2")'),
        
        # Generic [HyDowned] messages
        (r'println\("\[HyDowned\]\s*(.+?)"\)', f'Log.verbose("{category}", r"\1")'),
        
        # Plain println with string interpolation
        (r'println\("(.+?)"\)', f'Log.debug("{category}", r"\1")'),
    ]
    
    for pattern, replacement in patterns:
        new_line = re.sub(pattern, replacement, line)
        if new_line != line:
            return new_line
    
    return line

def process_file(filepath):
    """Process a single Kotlin file"""
    category = get_category_from_file(filepath)
    
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Check if file has println
    has_println = any('println' in line for line in lines)
    if not has_println:
        return False
    
    # Check if Log import exists
    has_log_import = any('import com.hydowned.util.Log' in line for line in lines)
    
    new_lines = []
    import_added = False
    
    for i, line in enumerate(lines):
        # Add Log import after last import if needed
        if not has_log_import and not import_added and line.strip().startswith('import '):
            # Look ahead to find last import
            j = i
            while j < len(lines) and (lines[j].strip().startswith('import ') or lines[j].strip() == ''):
                j += 1
            # If this is the last import block, add Log import after
            if j > i and (j >= len(lines) or not lines[j].strip().startswith('import')):
                # Find the actual last import line in this block
                last_import_idx = j - 1
                while last_import_idx > i and lines[last_import_idx].strip() == '':
                    last_import_idx -= 1
                
                if i == last_import_idx:
                    new_lines.append(line)
                    if not line.endswith('\n'):
                        new_lines.append('\n')
                    new_lines.append('import com.hydowned.util.Log\n')
                    import_added = True
                else:
                    new_lines.append(line)
            else:
                new_lines.append(line)
        elif 'println' in line:
            new_lines.append(convert_println(line, category))
        else:
            new_lines.append(line)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    
    return True

# Process all Kotlin files
for root, dirs, files in os.walk('.'):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            if process_file(filepath):
                print(f"Processed: {filepath}")

print("\nConversion complete!")
