package quoi.config

import quoi.api.colour.Colour
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.SelectorComponent

object VisualsSettings : SettingGroup(
    DungeonOptions,
    SelectorComponent("Visuals", "Dark", arrayListOf("Light", "Dark", "Onyx"))
) {
    val selectedTheme get() = (component as SelectorComponent<*>).selected
    val colour by colourPicker("Colour", Colour.CYAN).asParent()
    val classColors by selector(
        "Class colors",
        "Hypixel Style",
        listOf("Hypixel Style", "Noamm Style"),
        desc = "Noamm Style swaps the Archer and Berserk colours."
    )

    var rainbowSpeed by slider("Rainbow colour speed", 0.5f, 0.05f, 5.0f, 0.05f)
}
