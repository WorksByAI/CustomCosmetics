package quoi.compat

import com.customcosmetics.features.gameplay.dungeonapi.DungeonMenu
import com.customcosmetics.features.gameplay.dungeonapi.DungeonMenu.*
import java.util.function.BiConsumer
import net.minecraft.client.gui.screens.Screen
import quoi.QuoiMod
import quoi.api.colour.Colour
import quoi.api.colour.toHSB
import quoi.config.Config
import quoi.config.DungeonOptions
import quoi.module.ModuleManager
import quoi.module.settings.Saving
import quoi.module.settings.Setting
import quoi.module.settings.UIComponent
import quoi.module.settings.impl.*
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.screens.UIScreen

object CsbDungeonMenu : DungeonMenu {
    private var toggleNotifier: BiConsumer<String, Boolean>? = null
    private val entries by lazy {
        val options by lazy { controls(DungeonOptions.settings) }
        ModuleManager.modules.map { module ->
            val settings by lazy { controls(module.settings) }
            Entry(module.name, module.desc, { module.enabled }, module::toggleByUser, { settings }) {
                reset(module.settings)
                module.resetModuleControls()
            }
        } + Entry("Dungeon Options", "", null, null, { options }) { reset(DungeonOptions.settings) }
    }

    override fun modules(): List<Entry> {
        QuoiMod.ensureDungeonModules()
        return entries
    }

    override fun hudEditor(parent: Screen): Screen = UIScreen(HudManager.editor(), parent = parent)
    override fun save() = Config.save()
    private fun reset(settings: List<Setting<*>>) {
        for (setting in settings) {
            if (setting is Saving && setting is UIComponent<*>) setting.editFromMenu(setting::reset)
        }
    }
    override fun setToggleNotifier(notifier: BiConsumer<String, Boolean>) { toggleNotifier = notifier }
    fun notifyToggle(name: String, enabled: Boolean): Boolean {
        val notifier = toggleNotifier ?: return false
        notifier.accept(name, enabled)
        return true
    }

    private fun controls(settings: List<Setting<*>>, changed: () -> Unit = {}): List<Control> = settings
        .filterIsInstance<UIComponent<*>>()
        .filter { component -> settings.none { it === component.parent } }
        .map { control(it, changed) }

    private fun control(setting: UIComponent<*>, changed: () -> Unit): Control {
        fun edit(action: () -> Unit) {
            setting.editFromMenu(action)
            changed()
        }
        fun row(name: String, input: Input) = Control(name, setting.description, { true }, input, emptyList())
        val children = setting.children.map { control(it, changed) }.toMutableList()
        val input: Input = when (setting) {
            is SwitchComponent -> Toggle({ setting.value }) { edit { setting.value = !setting.value } }
            is SliderComponent<*> -> Number({ setting.value.toDouble() }, setting.min.toDouble(), setting.max.toDouble(), setting.incrementD) {
                edit { setting.set(it) }
            }
            is RangeSliderComponent<*> -> {
                val min = setting.min.toDouble()
                val max = setting.max.toDouble()
                children += row("Min", Number({ setting.value.first.toDouble() }, min, max, setting.incrementD) {
                    edit { setting.set(it, setting.value.second) }
                })
                children += row("Max", Number({ setting.value.second.toDouble() }, min, max, setting.incrementD) {
                    edit { setting.set(setting.value.first, it) }
                })
                Group()
            }
            is SelectorComponent<*> -> Choice(setting.options.map(::optionName), { setting.index }) { edit { setting.index = it } }
            is SegmentedComponent<*> -> Choice(setting.options.map(::optionName), { setting.index }) { edit { setting.index = it } }
            is MultiSelectComponent<*> -> {
                children += setting.options.mapIndexed { i, option ->
                    row(optionName(option), Toggle({ setting.isSelected(i) }) { edit { setting.toggle(i) } })
                }
                Group()
            }
            is ColourPickerComponent -> Color({ setting.value.rgb }, { color ->
                edit { setting.value = Colour.RGB(color).toHSB() }
            }, setting.allowAlpha, { setting.rainbow }, { edit(setting::toggleRainbow) })
            is KeybindComponent -> Key(setting::getKeyName, setting::bindFromMenu) { setting.value.clear() }
            is TextInputComponent -> Text({ setting.value }, { text -> edit { setting.value = text } }, setting.length, setting.placeholder)
            is ButtonComponent -> Button { edit(setting.value) }
            is HudComponent<*> -> {
                val hud = setting.value
                children += controls(hud.settings, hud::rebuild)
                if (hud.toggleable) Toggle({ hud.enabled }) { hud.enabled = !hud.enabled } else Group()
            }
            else -> Group()
        }
        return Control(setting.name, setting.description, { setting.isLocallyVisible }, input, children)
    }

    private fun optionName(option: Any?): String = if (option is Enum<*>) {
        option.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    } else option.toString()
}
