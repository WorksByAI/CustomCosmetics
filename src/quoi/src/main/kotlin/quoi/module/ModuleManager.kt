package quoi.module

import quoi.QuoiMod.logger
import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.module.settings.impl.KeybindComponent

object ModuleManager : EventListener {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            quoi.module.impl.general.LoadoutKeybinds,
            quoi.module.impl.general.PetKeybinds,
            quoi.module.impl.general.SlotBinds,
            quoi.module.impl.render.SlayerNametags,
            quoi.module.impl.misc.CustomTriggers,
            quoi.module.impl.dungeon.AutoBloodRush,
            quoi.module.impl.dungeon.autoclear.impl.AutoRoutes,
            quoi.module.impl.dungeon.autoclear.impl.InteractiveMap,
            quoi.module.impl.dungeon.AutoCroesus,
            quoi.module.impl.dungeon.AutoDoorOpener,
            quoi.module.impl.dungeon.BloodCamp,
            quoi.module.impl.dungeon.DungeonAbilities,
            quoi.module.impl.dungeon.DungeonBreaker,
            quoi.module.impl.dungeon.DungeonESP,
            quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers,
            quoi.module.impl.dungeon.secrets.Secrets,
            quoi.module.impl.floor7.ArrowAlign,
            quoi.module.impl.floor7.AutoInvincibility,
            quoi.module.impl.floor7.autoleap.Leaps,
            quoi.module.impl.floor7.BarrierBoom,
            quoi.module.impl.floor7.Crystals,
            quoi.module.impl.floor7.LightsDevice,
            quoi.module.impl.floor7.Relics,
            quoi.module.impl.floor7.SimonSays,
            quoi.module.impl.floor7.Terminals,
            quoi.module.impl.floor7.WitherCloak
        ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        modules.filter { it.alwaysActive }.forEach { it.onEnable() }

        modules.forEach { module ->
            module.keybinding.let {
                module.register(KeybindComponent("Key bind", it, desc = "Toggles the module"))
            }
        }

        logger.info("Loaded ${modules.size} modules.")

        on<KeyEvent.Press> { invokeKeybind(key, true) }
        on<KeyEvent.Release> { invokeKeybind(key, false) }
        on<MouseEvent.Click> { invokeKeybind(button - 100, state) }

        on<GuiEvent.Key.Press> { invokeKeybind(key, true) }
        on<GuiEvent.Key.Release> { invokeKeybind(key, false) }
        on<GuiEvent.Click> { invokeKeybind(button - 100, state) }
    }

    private fun invokeKeybind(key: Int, pressed: Boolean) {
        if (key == Keybinds.KEY_NONE || !quoi.QuoiMod.allowed) return

        modules.forEach { module ->
            module.settings.filterIsInstance<KeybindComponent>()
                .filter { it.value.key == key && it.value.isModifierDown() }
                .forEach { component ->
                    if (pressed) component.value.onPress?.invoke()
                    else component.value.onRelease?.invoke()
                }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}
