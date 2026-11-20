package quoi.module.impl.general

import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.ContainerInput
import quoi.api.events.GuiEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.impl.MapSetting
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.player.HotbarSwapperUtils
import quoi.utils.skyblock.player.container.ContainerUtils.hoveredSlot

object SlotBinds : Module("Slot Binds", desc = "Links inventory slots to hotbar slots for quick swaps.") {
    private val bindKey by keybind("Bind key", Keybinds.KEY_B, "Hold and click one inventory slot and one hotbar slot. Click a bound slot to unlink it.")
    private val swapKey by keybind("Swap key", Keybinds.KEY_LEFT_SHIFT, "Hold and left-click either linked slot to swap the items.")
    private val lock by switch("Lock bound slots", desc = "Prevents normal inventory clicks on linked slots.")
    private val display by selector("Show markers", "Always", listOf("Always", "Hovered", "Never"))
    private val links by MapSetting("Slot links", mutableMapOf<Int, Int>())
    private var selected: Int? = null
    @Suppress("unused")
    private val clearLinks by button("Clear slot bindings", "Removes all inventory and hotbar links.") {
        links.clear()
        selected = null
        Config.save()
    }
    private val colours = intArrayOf(0xFFCE9FFF.toInt(), 0xFF91B9FF.toInt(), 0xFF91E5BA.toInt(), 0xFFFFB692.toInt(),
        0xFFFF8CAA.toInt(), 0xFF79DED9.toInt(), 0xFFFF9292.toInt(), 0xFFFFDF8E.toInt(), 0xFFAEB4FF.toInt())

    init {
        on<GuiEvent.Removed> { if (screen is InventoryScreen) selected = null }
        on<GuiEvent.Slot.Click> {
            if (screen !is InventoryScreen || slotId !in 9..44 || slot.container !== player.inventory) return@on
            val inventorySlot = if (slotId < 36) slotId else links.entries.firstOrNull { it.value == slotId }?.key
            val hotbarSlot = inventorySlot?.let(links::get)
            if (bindKey.isDown() && button == 0 && actionType in bindActions && screen.menu.carried.isEmpty) {
                cancel()
                val previous = selected
                if (previous == null) {
                    if (inventorySlot != null && hotbarSlot != null) {
                        links.remove(inventorySlot)
                        Config.save()
                    } else selected = slotId
                } else {
                    selected = null
                    if ((previous >= 36) == (slotId >= 36)) return@on
                    val source = minOf(previous, slotId)
                    val target = maxOf(previous, slotId)
                    links.entries.removeIf { it.value == target }
                    links[source] = target
                    Config.save()
                }
                return@on
            }
            if (inventorySlot == null || hotbarSlot == null || hotbarSlot !in 36..44) return@on
            if (swapKey.isDown() && button == 0 && actionType in bindActions && screen.menu.carried.isEmpty) {
                cancel()
                HotbarSwapperUtils.swapWithHotbar(inventorySlot, hotbarSlot - 36)
            } else if (lock) cancel()
        }
        on<GuiEvent.Slot.Draw> {
            if (screen !is InventoryScreen || slot.index !in 9..44) return@on
            val index = slot.index
            val source = if (index < 36) index else links.entries.firstOrNull { it.value == index }?.key
            val target = source?.let(links::get)
            val hovered = if (display.selected == "Hovered") screen.hoveredSlot?.index else null
            val colour = when {
                selected == index -> -1
                target == null || target !in 36..44 || display.selected == "Never" -> return@on
                display.selected == "Hovered" && hovered != source && hovered != target -> return@on
                else -> colours[target - 36]
            }
            ctx.rect(slot.x - 1, slot.y - 1, 18, 1, colour)
            ctx.rect(slot.x - 1, slot.y + 16, 18, 1, colour)
            ctx.rect(slot.x - 1, slot.y, 1, 16, colour)
            ctx.rect(slot.x + 16, slot.y, 1, 16, colour)
        }
    }

    override fun onDisable() { selected = null }

    private val bindActions = setOf(ContainerInput.PICKUP, ContainerInput.QUICK_MOVE)
}
