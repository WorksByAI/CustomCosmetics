package quoi.config

import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.SelectorComponent

object DungeonOptions : Module("Click GUI") {
    val forceSkyblock by switch("Force skyblock")
    val forceDungeons by switch("Force dungeon").onValueChanged { _, new ->
        if (new) Dungeon.setFloor(dungeonFloor.selected)
    }
    val dungeonFloor: SelectorComponent<Floor> by selector("Floor", Floor.F7).childOf(::forceDungeons)
        .onValueChanged { _, new -> if (forceDungeons) Dungeon.setFloor(new.selected) }
}
