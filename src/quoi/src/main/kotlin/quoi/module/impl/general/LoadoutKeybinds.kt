package quoi.module.impl.general

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.skyblock.location.Location
import quoi.compat.GameAccess
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.Keybinding
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.ContainerUtils.clickSlot
import quoi.utils.skyblock.player.container.task.ContainerManager

/** Menu controls from Athen and opt-in equip and close behavior from Nebulune. */
object LoadoutKeybinds : Module("Loadout Keybinds", desc = "Equip SkyBlock loadouts and navigate the Loadouts menu with keybinds.") {
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
    private val slotNames = (1..loadoutSlots.size).map { "Slot $it" }
    private val menuTitle = Regex("""^\((\d+)/(\d+)\) Loadouts$""")
    private val cancelOther by switch("Cancel all other clicks")
    private val keyOverride by keybind("Key override", Keybinds.KEY_LEFT_CONTROL).childOf(::cancelOther)
    private val ping by slider("Ping", 250, 10, 1000, unit = "ms", desc = "Minimum time between menu keybind actions.")
    private val general by text("General keybinds")
    private val useHotbar by switch("Use hotbar binds", true).childOf(::general)
    private val previous by keybind("Previous page").childOf(::general)
    private val next by keybind("Next page").childOf(::general)
    private val swap by switch("Swap key")
    private val swapKey by keybind("Swap keybind").childOf(::swap)
    private val swapFirst by selector("Swap slot 1", slotNames[0], slotNames).childOf(::swap)
    private val swapSecond by selector("Swap slot 2", slotNames[1], slotNames).childOf(::swap)
    private val customKeys by text("Slot keybinds")
    private val bindings = slotNames.map { register(KeybindComponent(it, Keybinds.KEY_NONE).childOf(::customKeys)).value }
    private val autoClose by switch("Auto Close After Use", desc = "Closes the Loadouts menu after equipping a slot.")
    private val autoEquip by switch("Auto equip", desc = "Opens Loadouts and equips a slot when its custom key is pressed in game.")
    private val equipWhileMoving by switch("Equip while moving").childOf(::autoEquip)
    private val resetOnOpen by switch("Reset on GUI open", true, "Cancels a pending equip when an unrelated screen opens.").childOf(::autoEquip)
    private val clickDelay by slider("Click delay", 1, 0, 8, unit = "ticks").childOf(::autoEquip)
    private val closeDelay by slider("Close delay", 1, 0, 8, unit = "ticks").childOf(::autoClose)
    private val delayVariance by slider("Max delay variety", 1, 0, 5, unit = "ticks")

    private data class Equip(val index: Int, val expires: Int, var readyAt: Int? = null)
    private data class Close(val screen: Screen, val at: Int)
    private var equip: Equip? = null
    private var close: Close? = null
    private var tick = 0
    private var lastClick = 0L

    init {
        on<GuiEvent.Key.Press> { if (handleMenuKey(screen, key)) cancel() }
        on<GuiEvent.Click> { if (state && handleMenuKey(screen, button - 100)) cancel() }
        on<GuiEvent.Slot.Click> {
            if (!autoClose || button != 0 || actionType != ContainerInput.PICKUP ||
                slotId !in loadoutSlots || loadoutMenu(screen) == null) return@on
            if (canEquip(slot.item)) scheduleClose(screen)
        }
        on<KeyEvent.Press> { if (requestEquip(key)) cancel() }
        on<MouseEvent.Click> { if (state && requestEquip(button - 100)) cancel() }
        on<GuiEvent.Open.Post> {
            if (resetOnOpen && loadoutMenu(screen) == null) equip = null
        }
        on<TickEvent.Start> { update() }
        on<WorldEvent.Change> { clear() }
        command.sub("equiploadout") { slot: Int ->
            if (!active || !autoEquip || slot !in 1..loadoutSlots.size) return@sub
            startEquip(slot - 1)
        }.description("Equips a Loadouts slot (1–12) through its menu.")
    }

    override fun onDisable() = clear()

    private fun clear() {
        equip = null
        close = null
        lastClick = 0L
    }

    private fun loadoutMenu(screen: Screen?): AbstractContainerScreen<*>? {
        if (!Location.inSkyblock || screen !is AbstractContainerScreen<*>) return null
        val menu = screen.menu as? ChestMenu ?: return null
        return screen.takeIf { menu.rowCount == 6 && menuTitle.matches(it.title.string) }
    }

    private fun handleMenuKey(screen: Screen, key: Int): Boolean {
        val menu = loadoutMenu(screen) ?: return false
        val (page, pages) = menuTitle.matchEntire(menu.title.string)!!.destructured
        val blockOther = cancelOther && !keyOverride.isDown() && key != Keybinds.KEY_ESCAPE &&
            key != keyCode(KeyMappingHelper.getBoundKeyOf(mc.options.keyInventory))
        if (lastClick != 0L && System.nanoTime() - lastClick < ping * 1_000_000L) return blockOther
        when {
            matches(previous, key) -> return page.toInt() <= 1 || click(menu, 17)
            matches(next, key) -> return page.toInt() >= pages.toInt() || click(menu, 44)
            swap && matches(swapKey, key) -> {
                val first = loadoutSlots[swapFirst.index]
                val second = loadoutSlots[swapSecond.index]
                if (first != second && !menu.menu.getSlot(first).item.isEmpty && !menu.menu.getSlot(second).item.isEmpty) {
                    equip(menu, if (canEquip(menu.menu.getSlot(first).item)) first else second)
                }
                return true
            }
        }
        val index = if (useHotbar) {
            mc.options.keyHotbarSlots.indexOfFirst { keyCode(KeyMappingHelper.getBoundKeyOf(it)) == key }
        } else bindings.indexOfFirst { matches(it, key) }
        val slot = loadoutSlots.getOrNull(index) ?: return blockOther
        equip(menu, slot)
        return true
    }

    private fun requestEquip(key: Int): Boolean {
        if (!autoEquip || GameAccess.screen() != null) return false
        val index = bindings.indexOfFirst { matches(it, key) }
        return index >= 0 && startEquip(index)
    }

    private fun startEquip(index: Int): Boolean {
        if (!Location.inSkyblock || equip != null || GameAccess.screen() != null || ContainerManager.activeTask != null) return false
        val input = mc.player?.input?.keyPresses ?: return false
        if (!equipWhileMoving && (input.forward() || input.backward() || input.left() || input.right() || input.jump())) return false
        equip = Equip(index, tick + 40)
        ChatUtils.command("loadout")
        return true
    }

    private fun update() {
        tick++
        close?.takeIf { tick >= it.at }?.let { request ->
            close = null
            if (autoClose && GameAccess.screen() === request.screen && loadoutMenu(request.screen) != null) {
                mc.player?.closeContainer()
            }
        }
        val request = equip ?: return
        if (!autoEquip || tick >= request.expires) {
            equip = null
            return
        }
        val menu = loadoutMenu(GameAccess.screen()) ?: return
        if (ContainerUtils.current?.let { it.itemsLoaded && it.menu === menu.menu } != true) return
        val readyAt = request.readyAt ?: (tick + clickDelay + variance()).also { request.readyAt = it }
        if (tick >= readyAt) {
            equip = null
            equip(menu, loadoutSlots[request.index])
        }
    }

    private fun equip(screen: AbstractContainerScreen<*>, slot: Int) {
        if (canEquip(screen.menu.getSlot(slot).item) && click(screen, slot) && autoClose) scheduleClose(screen)
    }

    private fun click(screen: AbstractContainerScreen<*>, slot: Int): Boolean {
        val player = mc.player ?: return false
        if (player.containerMenu !== screen.menu || screen.menu.getSlot(slot).item.isEmpty) return false
        lastClick = System.nanoTime()
        player.clickSlot(slot, screen.menu.containerId)
        return true
    }

    private fun scheduleClose(screen: Screen) {
        close = Close(screen, tick + closeDelay + variance())
    }

    private fun variance() = (0..delayVariance).random()

    private fun canEquip(stack: ItemStack) = !stack.isEmpty &&
        stack.getCleanLore().any { it.contains("Left-click to equip!", ignoreCase = true) }

    private fun matches(binding: Keybinding, key: Int) =
        key != Keybinds.KEY_NONE && binding.key == key && binding.isModifierDown()

    private fun keyCode(key: InputConstants.Key) =
        if (key.type == InputConstants.Type.MOUSE) key.value - 100 else key.value
}
