package com.hydowned.systems

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.modules.interaction.Interactions
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hydowned.components.DownedComponent
import com.hydowned.config.DownedConfig
import com.hydowned.util.Log


/**
 * Removes the Interactions and Interactable components when player is downed, restores them when revived.
 *
 * This completely blocks ALL interactions while downed:
 * - Breaking blocks
 * - Placing blocks
 * - Using items
 * - Opening doors
 * - Opening chests/containers
 * - Interacting with entities
 * - Everything
 *
 * Uses the same approach as BuilderTools when disabling entity interactions.
 * Both components are stored and restored on revival.
 */
class DownedRemoveInteractionsSystem(
    private val config: DownedConfig
) : RefChangeSystem<EntityStore, DownedComponent>() {

    private val interactionsComponentType: ComponentType<EntityStore, Interactions> =
        Interactions.getComponentType()

    private val interactableComponentType: ComponentType<EntityStore, Interactable> =
        Interactable.getComponentType()

    // Store the removed components so we can restore them on revival
    private val savedInteractions = mutableMapOf<Ref<EntityStore>, Interactions>()
    private val hadInteractable = mutableSetOf<Ref<EntityStore>>()

    private val query = Query.and(
        Player.getComponentType()
    )

    override fun componentType() = DownedComponent.getComponentType()

    override fun getQuery(): Query<EntityStore> = query

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        Log.verbose("RemoveInteractions", "onComponentAdded triggered for downed player")

        // Save and remove the Interactions component
        val interactions = commandBuffer.getComponent(ref, interactionsComponentType)
        if (interactions != null) {
            savedInteractions[ref] = interactions
            commandBuffer.removeComponent(ref, interactionsComponentType)
            Log.verbose("RemoveInteractions", "Removed Interactions component")
        } else {
            Log.warning("RemoveInteractions", "No Interactions component found")
        }

        // Remove the Interactable component (and track if it existed)
        val hadInteractableComponent = commandBuffer.getComponent(ref, interactableComponentType) != null
        if (hadInteractableComponent) {
            hadInteractable.add(ref)
            commandBuffer.removeComponent(ref, interactableComponentType)
            Log.verbose("RemoveInteractions", "Removed Interactable component - all interactions blocked")
        } else {
            Log.warning("RemoveInteractions", "No Interactable component found")
        }
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DownedComponent?,
        newComponent: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Not needed
    }

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DownedComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Restore the Interactions component when revived
        val interactions = savedInteractions.remove(ref)
        if (interactions != null) {
            commandBuffer.addComponent(ref, interactionsComponentType, interactions)
            Log.verbose("RemoveInteractions", "Restored Interactions component")
        }

        // Restore the Interactable component if it existed before
        if (hadInteractable.remove(ref)) {
            commandBuffer.ensureComponent(ref, interactableComponentType)
            Log.verbose("RemoveInteractions", "Restored Interactable component - interactions re-enabled")
        }
    }
}
