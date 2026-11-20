package quoi.config

import com.google.gson.*
import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.events.GameEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.once
import quoi.module.ModuleManager
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.module.impl.floor7.Terminals
import quoi.module.settings.Saving
import java.io.File
import kotlin.math.roundToInt

/**
 * This class handles loading and saving Modules and their settings.
 *
 * @author Stivais
 */
object Config : EventListener {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false

    private val configFile = File(mc.gameDirectory, "config/customcosmetics/dungeon/settings.json").apply {
        try {
            parentFile?.mkdirs()
            createNewFile()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
    }

    init {
        once<GameEvent.Unload> { save() }
    }

    fun load(source: String? = null) {
        loaded = false
        try {
            with(source ?: configFile.bufferedReader().use { it.readText() }) {
                val jsonElement = if (isBlank()) JsonArray() else JsonParser.parseString(this)
                val jsonArray = if (jsonElement.isJsonArray) jsonElement.asJsonArray else error("Expected module array")
                var migrateDevice = false
                if (source == null) {
                    try {
                        val device = DeviceConfig.read(configFile.toPath().parent.parent)
                        val legacy = DeviceConfig.takeDungeon(jsonArray)
                        for ((name, value) in legacy.entrySet()) if (!device.has(name)) device.add(name, value)
                        DeviceConfig.restoreDungeon(jsonArray, device)
                        migrateDevice = legacy.size() != 0
                    } catch (e: Exception) {
                        logger.error("Could not read Device settings", e)
                    }
                }
                migrateMapAppearance(jsonArray)

                for (modules in jsonArray) {
                    val moduleObj = modules?.asJsonObject ?: continue
                    val name = moduleObj.get("name").asString
                    val module = if (name == DungeonOptions.name) DungeonOptions else ModuleManager.getModuleByName(name) ?: continue
                    if (moduleObj.get("enabled").asBoolean != module.enabled) module.toggle()
                    val settingsElement = moduleObj.get("settings") ?: continue
                    if (module === Terminals) migrateTerminalLeapDelay(settingsElement)

                    when {
                        settingsElement.isJsonArray -> {
                            for (j in settingsElement.asJsonArray) {
                                val settingObj = j?.asJsonObject?.entrySet() ?: continue
                                val setting = module.getSettingByName(settingObj.firstOrNull()?.key) ?: continue
                                if (setting is Saving) setting.read(settingObj.first().value)
                            }
                        }

                        settingsElement.isJsonObject -> {
                            val settingObj = settingsElement.asJsonObject.entrySet()
                            for ((key, value) in settingObj) {
                                val setting = module.getSettingByName(key) ?: continue
                                if (setting is Saving) setting.read(value)
                            }
                        }
                    }
                }
                loaded = true
                if (migrateDevice) save()
            }
        } catch (e: Exception) {
            println("Error loading config.\n${e.message}")
            logger.error("Error initializing module config", e)
            if (source != null) throw e
        }
    }

    private fun migrateMapAppearance(modules: JsonArray) {
        val legacy = modules.filterIsInstance<JsonObject>()
            .firstOrNull { it.get("name")?.asString == "Dungeon Map" } ?: return
        val settings = legacy.get("settings") as? JsonObject ?: return
        val hud = settings.get("Dungeon map") as? JsonObject ?: return
        for ((name, value) in hud.entrySet()) {
            (InteractiveMap.getSettingByName("Map appearance.$name") as? Saving)?.read(value)
        }
    }

    private fun migrateTerminalLeapDelay(settings: JsonElement) {
        val values = when {
            settings.isJsonObject -> settings.asJsonObject.asMap()
            settings.isJsonArray -> settings.asJsonArray.filterIsInstance<JsonObject>()
                .flatMap { it.entrySet() }.associate { it.key to it.value }
            else -> return
        }
        if ("Leap delay ticks" in values) return
        val enabled = values["Leap delay"] as? JsonPrimitive ?: return
        if (!enabled.isBoolean) return
        val seconds = (values["Leap delay time"] as? JsonPrimitive)
            ?.takeIf { it.isNumber }?.asDouble?.takeIf { it.isFinite() } ?: 0.5
        val ticks = if (enabled.asBoolean) (seconds * 20).coerceIn(0.0, 100.0).roundToInt() else 0
        (Terminals.getSettingByName("Leap delay ticks") as? Saving)?.read(JsonPrimitive(ticks))
    }

    private fun snapshotJson(): JsonArray = JsonArray().apply {
        for (module in ModuleManager.modules + DungeonOptions) {
            add(JsonObject().apply {
                add("name", JsonPrimitive(module.name))
                add("enabled", JsonPrimitive(module.enabled))
                add("settings", JsonObject().apply {
                    for (setting in module.settings) {
                        if (setting is Saving) add(setting.jsonName, setting.write())
                    }
                })
            })
        }
    }

    fun snapshot(): String = gson.toJson(snapshotJson())

    fun save() {
        if (!loaded) return
        try {
            val jsonArray = snapshotJson()
            val remaining = jsonArray.deepCopy()
            val config = try {
                DeviceConfig.merge(configFile.toPath().parent.parent, DeviceConfig.takeDungeon(remaining))
                remaining
            } catch (e: Exception) {
                logger.error("Could not save Device settings. Keeping device modules in the dungeon config.", e)
                jsonArray
            }
            writeConfigAtomically(configFile) { gson.toJson(config, it) }
        } catch (e: Exception) {
            println("Error saving config.\n${e.message}")
            logger.error("Error saving config.", e)
        }
    }
}
