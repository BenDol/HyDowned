package com.hydowned.network

import com.hydowned.ModPlugin
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter
import com.hypixel.hytale.server.core.universe.PlayerRef

/**
 * Packet filter
 *
 * Only blocks interaction packet (ID 290) when downed, specifically:
 * - Ability1 interactions
 * - Primary interactions (left-click)
 */
class DownedPlayerPacketFilter : PlayerPacketFilter {

    override fun test(playerRef: PlayerRef, packet: Packet): Boolean {
        // Only filter packet ID 290 (SyncInteractionChains)
        if (packet.id != 290) {
            return false
        }

        val chains = packet as SyncInteractionChains
        val managers = ModPlugin.instance?.managers ?: return false
        val modPlayer = managers.playerManager.get(playerRef) ?: return false

        // Only block if player is downed
        if (!modPlayer.asDownable.isDowned()) {
            return false
        }

        // Check if any interaction in the chain is Ability1 or Primary
        for (item in chains.updates) {
            val type = item.interactionType
            if (type == InteractionType.Ability1 || type == InteractionType.Primary) {
                return true
            }
        }

        return false
    }
}