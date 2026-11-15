package quoi

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import quoi.api.commands.QuoiCommand
import quoi.api.events.core.EventListener
import quoi.config.Config
import quoi.module.ModuleManager
import quoi.utils.ui.hud.HudManager

// todo add docs to some utils and apis
object QuoiMod : ClientModInitializer, EventListener {

    const val MOD_ID = "quoi"
    private val confirmation by lazy {
        net.fabricmc.loader.api.FabricLoader.getInstance()
            .getEntrypoints("csb-dungeon-confirmed", java.util.function.BooleanSupplier::class.java).single()
    }
    val allowed: Boolean get() = confirmation.asBoolean
    val mc: Minecraft get() = Minecraft.getInstance()
    val scope = CoroutineScope(EmptyCoroutineContext)
    val logger: Logger = LogManager.getLogger("quoi")

    @Suppress("UNUSED_EXPRESSION")
    override fun onInitializeClient() {
        ModuleManager.initialise()
        // Load the shared services and their upstream event subscriptions in dependency order.
        quoi.api.events.core.EventDispatcher
        quoi.api.skyblock.location.Location
        quoi.api.skyblock.dungeon.Dungeon
        quoi.api.skyblock.dungeon.Floor7
        quoi.api.skyblock.dungeon.terminals.TerminalUtils
        quoi.api.skyblock.SkyblockPlayer
        quoi.api.ServerInfo
        quoi.utils.EntityUtils
        quoi.utils.skyblock.PartyUtils
        quoi.utils.skyblock.SplitsManager
        quoi.utils.skyblock.player.SwapManager
        quoi.utils.skyblock.player.PetUtils
        quoi.utils.skyblock.player.LeapManager
        quoi.utils.skyblock.player.MovementUtils
        quoi.utils.skyblock.player.RotationUtils
        quoi.utils.skyblock.player.simulation.PlayerSimulation
        quoi.utils.skyblock.player.interact.AuraManager
        quoi.utils.skyblock.player.container.ContainerUtils
        quoi.utils.skyblock.player.container.task.ContainerManager
        quoi.utils.skyblock.item.BreakerUtils
        quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
        QuoiCommand.init()
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
            .register { ensureDungeonModules() }
    }

    private var initialized = false

    fun ensureDungeonModules() {
        if (initialized || !allowed) return
        HudManager.init()
        Config.load()
        initialized = true
    }
}
