package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.dungeon.enums.DungeonPlayer

object SkyblockPlayerEvent {
    class Leap(val target: DungeonPlayer) : Event()
}
