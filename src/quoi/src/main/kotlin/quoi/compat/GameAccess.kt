package quoi.compat

import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import quoi.QuoiMod.mc

object GameAccess {
    //? if <26.2 {
    /*fun screen() = mc.screen
    fun setScreen(screen: Screen?) = mc.setScreen(screen)
    fun hud() = mc.gui
    fun camera() = mc.gameRenderer.mainCamera
    fun fov() = camera().fov
    fun hudHidden() = mc.options.hideGui
    *///?} else {
    fun screen() = mc.gui.screen()
    fun setScreen(screen: Screen?) = mc.gui.setScreen(screen)
    fun hud() = mc.gui.hud
    fun camera() = mc.gameRenderer.mainCamera()
    fun fov() = camera().fov
    fun hudHidden() = hud().isHidden
    //?}

    private val legacyColors = mutableMapOf<String, net.minecraft.world.level.block.Block>()

    fun colored(material: String, color: String): net.minecraft.world.level.block.Block {
        return legacyColors.getOrPut("$material:$color") {
            val colorName = color.replace(Regex("[A-Z]")) { "_" + it.value.lowercase() }
            val base = material.removePrefix("DYED_").lowercase()
            val id = Identifier.withDefaultNamespace("${colorName}_$base")
            val registry = BuiltInRegistries.BLOCK
            if (registry.containsKey(id)) return@getOrPut registry.getValue(id)
            registry.getValue(id)
        }
    }
}
