package quoi.module.impl.misc

import quoi.QuoiMod.mc
import quoi.api.customtriggers.TriggerManager
import quoi.api.customtriggers.ui.TriggerEditor
import quoi.compat.GameAccess
import quoi.module.Module
import quoi.utils.ui.screens.UIScreen

object CustomTriggers : Module(
    "Custom Triggers",
    desc = "Run configurable actions in SkyBlock when events and conditions match.",
    tag = Tag.BETA
) {
    @Suppress("unused")
    private val editor by button("Open editor") { openEditor() }

    init {
        TriggerManager.init()
        command.sub("ct") { openEditor() }.description("Opens Custom Triggers editor.")
    }

    private fun openEditor() {
        val parent = GameAccess.screen()
        mc.schedule { GameAccess.setScreen(UIScreen(TriggerEditor().build(), parent = parent)) }
    }

    override fun onDisable() = TriggerManager.reset()
}