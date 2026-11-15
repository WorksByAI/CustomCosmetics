package quoi.api.commands

import quoi.api.commands.internal.BaseCommand

object QuoiCommand {
    val command = BaseCommand("csbdungeon") {}
    val devCommand = BaseCommand("csbdungeon-debug")

    fun init() {
        command.requires("Open Cheating and confirm the warning first.") { quoi.QuoiMod.allowed }.register()
    }
}
