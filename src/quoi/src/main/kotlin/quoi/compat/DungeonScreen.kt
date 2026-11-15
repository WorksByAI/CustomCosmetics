package quoi.compat

import net.minecraft.client.gui.screens.Screen
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.screens.UIScreen
import java.util.function.BiFunction

object DungeonScreen : BiFunction<Screen, Boolean, Screen> {
    override fun apply(parent: Screen, hud: Boolean): Screen {
        quoi.QuoiMod.ensureDungeonModules()
        return UIScreen(if (hud) HudManager.editor() else ClickGui.clickGui, parent = parent)
    }
}
