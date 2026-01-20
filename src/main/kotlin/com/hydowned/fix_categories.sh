#!/bin/bash

# Fix category references to use actual category strings

# HyDownedPlugin
sed -i 's/Log\.separator(category)/Log.separator("Plugin")/g' HyDownedPlugin.kt
sed -i 's/Log\.error(category,/Log.error("Plugin",/g' HyDownedPlugin.kt
sed -i 's/Log\.warning(category,/Log.warning("Plugin",/g' HyDownedPlugin.kt
sed -i 's/Log\.info(category,/Log.info("Plugin",/g' HyDownedPlugin.kt
sed -i 's/Log\.verbose(category,/Log.verbose("Plugin",/g' HyDownedPlugin.kt
sed -i 's/Log\.debug(category,/Log.debug("Plugin",/g' HyDownedPlugin.kt

# Commands
sed -i 's/Log\.\(.*\)(category,/Log.\1("GiveUpCommand",/g' commands/GiveUpCommand.kt

# Listeners
sed -i 's/Log\.\(.*\)(category,/Log.\1("PlayerReady",/g' listeners/PlayerReadyEventListener.kt

# Network
sed -i 's/Log\.\(.*\)(category)/Log.\1("PacketInterceptor")/g' network/DownedPacketInterceptor.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("PacketInterceptor",/g' network/DownedPacketInterceptor.kt

# Systems
sed -i 's/Log\.\(.*\)(category)/Log.\1("CollisionDisable")/g' systems/DownedCollisionDisableSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("CollisionDisable",/g' systems/DownedCollisionDisableSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("DamageImmunity")/g' systems/DownedDamageImmunitySystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("DamageImmunity",/g' systems/DownedDamageImmunitySystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("HealingSuppression")/g' systems/DownedHealingSuppressionSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("HealingSuppression",/g' systems/DownedHealingSuppressionSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("HudCleanup")/g' systems/DownedHudCleanupSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("HudCleanup",/g' systems/DownedHudCleanupSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("Invisibility")/g' systems/DownedInvisibilitySystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("Invisibility",/g' systems/DownedInvisibilitySystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("LoginCleanup")/g' systems/DownedLoginCleanupSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("LoginCleanup",/g' systems/DownedLoginCleanupSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("LogoutHandler")/g' systems/DownedLogoutHandlerSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("LogoutHandler",/g' systems/DownedLogoutHandlerSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("PacketInterceptor")/g' systems/DownedPacketInterceptorSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("PacketInterceptor",/g' systems/DownedPacketInterceptorSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("PhantomBody")/g' systems/DownedPhantomBodySystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("PhantomBody",/g' systems/DownedPhantomBodySystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("PlayerScale")/g' systems/DownedPlayerScaleSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("PlayerScale",/g' systems/DownedPlayerScaleSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("RemoveInteractions")/g' systems/DownedRemoveInteractionsSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("RemoveInteractions",/g' systems/DownedRemoveInteractionsSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("PhantomAnimation")/g' systems/PhantomBodyAnimationSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("PhantomAnimation",/g' systems/PhantomBodyAnimationSystem.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("ReviveInteraction")/g' systems/ReviveInteractionSystem.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("ReviveInteraction",/g' systems/ReviveInteractionSystem.kt

# Utils
sed -i 's/Log\.\(.*\)(category)/Log.\1("CleanupHelper")/g' util/DownedCleanupHelper.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("CleanupHelper",/g' util/DownedCleanupHelper.kt

sed -i 's/Log\.\(.*\)(category)/Log.\1("DeathTracker")/g' util/PendingDeathTracker.kt
sed -i 's/Log\.\(.*\)(category,/Log.\1("DeathTracker",/g' util/PendingDeathTracker.kt

