package quoi.config

import quoi.annotations.Internal
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.TextComponent

@Internal
object DungeonData : SettingGroup(DungeonOptions, TextComponent("").hide()) {
    var currentPet by textInput("Current pet", length = 0).hide()
}
