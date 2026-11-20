package quoi.module

import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig.Part
import quoi.QuoiMod
import quoi.annotations.AlwaysActive
import quoi.api.commands.QuoiCommand
import quoi.api.events.core.AreaBoundListener
import quoi.api.events.core.removeAsyncScope
import quoi.api.input.Keybinds
import quoi.api.skyblock.location.Area
import quoi.module.settings.Setting
import quoi.module.settings.SettingsDSL
import quoi.module.settings.impl.Keybinding
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Shortcuts
import quoi.utils.ui.hud.HudDSL

abstract class Module(
    val name: String,
    override val area: Area? = null,
    override val subarea: String? = null,
    val key: Int = Keybinds.KEY_NONE,
    @Transient val desc: String = "",
    toggled: Boolean = false,
    val tag: Tag = Tag.NONE
) : SettingsDSL(), HudDSL, Shortcuts, AreaBoundListener {

    private val devicePart = Part.forDungeonName(name) != null
    private var deviceRunning = false

    override val running: Boolean
        get() = (enabled || alwaysActive) && QuoiMod.allowed && (!devicePart || QuoiMod.deviceEnabled)

    inline val active: Boolean
        get() = running && inEnvironment()

    @Transient
    val category: Category = getCategory(this::class.java) ?: Category.RENDER

    val keybinding: Keybinding = Keybinding(key).apply { onPress = ::onKeybind }  // todo on press/release/hold

    var enabled: Boolean = toggled
        private set
    private val defaultEnabled = toggled

    protected inline val command get() = QuoiCommand.command

    @Transient
    val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    val settings: ArrayList<Setting<*>> = ArrayList()

    open fun onEnable() {}

    open fun onDisable() {}

    open fun onKeybind() {
        if (quoi.compat.GameAccess.screen() != null) return
        toggleByUser()
    }

    fun toggleByUser() {
        toggle()
        if (!quoi.compat.CsbDungeonMenu.notifyToggle(name, enabled)) {
            modMessage("$name ${if (enabled) "§aenabled" else "§cdisabled"}§r.", name.hashCode())
        }
    }

    fun toggle() {
        enabled = !enabled
        if (devicePart) syncDeviceGate()
        else updateLifecycle(enabled)
    }

    fun syncDeviceGate() {
        if (!devicePart) return
        val next = enabled && QuoiMod.deviceEnabled
        if (next == deviceRunning) return
        deviceRunning = next
        updateLifecycle(next)
    }

    private fun updateLifecycle(running: Boolean) {
        if (running) onEnable()
        else {
            removeAsyncScope()
            onDisable()
        }
    }

    fun resetModuleControls() {
        keybinding.clear()
        keybinding.key = key
        if (enabled != defaultEnabled) toggle()
    }

    fun addSettings(vararg setArray: Setting<*>) {
        setArray.forEach {
            settings.add(it)
        }
    }

    override fun <K : Setting<T>, T> register(setting: K): K {
        addSettings(setting)
        return setting
    }

    fun getSettingByName(name: String?): Setting<*>? =
        settings.firstOrNull { it.jsonName.equals(name, true) }

    enum class Tag(val desc: String = "") {
        NONE,
        LEGACY("A rewrite is currently planned. This module is no longer updated."),
        BETA("This feature is in beta. Issues may occur; no need to report bugs.")
    }

    private companion object {
        private fun getCategory(clazz: Class<out Module>): Category? =
            Category.entries.find { clazz.`package`.name.contains(it.name, true) }
    }
}
