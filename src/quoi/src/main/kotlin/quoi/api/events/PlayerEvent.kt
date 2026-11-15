package quoi.api.events

import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.HitResult
import quoi.api.events.core.CancellableEvent

/** Adapted from Quoi's player interaction event, based on NoammAddons (CC0-1.0). */
object PlayerEvent {
    class RightClick(val item: ItemStack, val hand: InteractionHand, val hitResult: HitResult?) : CancellableEvent()
}
