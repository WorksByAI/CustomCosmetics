package quoi.module.impl.dungeon.puzzlesolvers.impl

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock.POWERED
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.Puzzle
import quoi.api.skyblock.dungeon.enums.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.ChatUtils
import quoi.utils.Scheduler
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.toFixed
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawText
import quoi.utils.render.drawTracer
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpLandingY
import quoi.utils.skyblock.player.interact.AuraManager

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/WaterSolver.kt
 */
object WaterBoardSolver : SettingGroup(PuzzleSolvers, "Water board"), Repositionable {

    private val solver by switch("Solver", desc = "Shows the solution to the water board puzzle.")
    private val tracer by switch("Tracer", true, desc = "Shows a tracer to the next lever.").childOf(::solver)
    private val tracerFirst by colourPicker("First", Colour.MINECRAFT_GREEN, true, desc = "Colour for the first tracer.").childOf(::tracer)
    private val tracerSecond by colourPicker("Second", Colour.MINECRAFT_GOLD, true, desc = "Colour for the second tracer.").childOf(::tracer)
    private val optimised by switch("Optimised solutions", desc = "Uses optimised solutions for the water board puzzle.").asParent()
    private val auto by switch("Auto").asParent()
    private val triggerbot by switch("Triggerbot", desc = "Automatically clicks the next correct water board lever when you look at it.")

    private var waterSolutions: JsonObject = PuzzleSolvers.loadSolution("waterSolutions.json", JsonObject())

    init {
        scheduleLoop(10) {
            if (!module.active) return@scheduleLoop
            if (solver || auto || triggerbot) scan(optimised)
        }

        on<DungeonEvent.Room.Enter> {
            if (room?.name == "Water Board" || leverStates.isNotEmpty()) {
                reset()
                restoring = room?.name == "Water Board" && startingBoard?.pos == room.clayPos
            }
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            if (patternIdentifier == -1 || solutions.isEmpty()) return@on

            if (tracer) {
                val firstSolution = solutionList.firstOrNull()?.first ?: return@on
                ctx.drawTracer(Vec3(firstSolution.leverPos).add(.5, .5, .5), tracerFirst, depth = false)

                if (solutionList.size > 1 && firstSolution.leverPos != solutionList[1].first.leverPos) {
                    ctx.drawLine(
                        listOf(Vec3(firstSolution.leverPos).add(.5, .5, .5), Vec3(solutionList[1].first.leverPos).add(.5, .5, .5)),
                        colour = tracerSecond, depth = false
                    )
                }
            }

            solutions.forEach { (lever, times) ->
                times.drop(lever.i).forEachIndexed { index, time ->
                    val timeInTicks = (time * 20).toInt()
                    val str = when (openedWaterTicks) {
                        -1 if timeInTicks == 0 -> "§a§lCLICK ME!"
                        -1 -> "§e${time}s"
                        else -> (openedWaterTicks + timeInTicks - tickCounter).takeIf { it > 0 }?.let { "§e${(it / 20f).toFixed()}s" } ?: "§a§lCLICK ME!"
                    }
                    ctx.drawText(ChatUtils.literal(str), Vec3(lever.leverPos).add(0.5, (index + lever.i) * 0.5 + 1.5, 0.5), scale = 1f, depth = true)
                }
            }
        }

        on<PacketEvent.ReceivedPost, ClientboundBlockUpdatePacket> {
            observeLever(packet.pos, packet.blockState)
        }

        on<PacketEvent.ReceivedPost, ClientboundSectionBlocksUpdatePacket> {
            if (leverStates.isNotEmpty()) packet.runUpdates(::observeLever)
        }

        on<TickEvent.Server> {
            if (solver || auto || triggerbot) tickCounter++
        }

        on<TickEvent.End> {
            if (!auto) return@on
            if (ClearExecutor.active) return@on
            val room = Dungeon.currentRoom ?: return@on
            if (room.isSolved() || kotlin.math.abs(player.y - 59.0) > 0.1 || PuzzleSolvers.screenBlocksAuto || atChest ||
                !readyForClick()) return@on

            repositionTicker?.let {
                if (it.tick()) Scheduler.scheduleTask {
                    repositionTicker = null
                }
                return@on
            }

            if (restoring) {
                val baseline = startingBoard?.levers ?: return@on
                if (leverStates.isEmpty()) return@on
                val lever = playableLevers.firstOrNull { leverStates[it] != baseline[it] } ?: return@on
                if (moveToLever(room, lever)) requestLever(lever)
                return@on
            }
            if (patternIdentifier == -1 || solutions.isEmpty()) return@on
            val (lever, time) = solutionList.firstOrNull() ?: return@on chest(room)
            if (moveToLever(room, lever) && readyAt(time)) requestLever(lever)
        }

        on<TickEvent.End> {
            if (!triggerbot || auto || restoring || patternIdentifier == -1 || solutions.isEmpty() || !readyForClick()) return@on
            val (lever, time) = solutionList.firstOrNull() ?: return@on
            if (readyAt(time) && PuzzleSolvers.triggerBlock(lever.leverPos)) expectLever(lever)
        }

        on<WorldEvent.Change> {
            reset()
            startingBoard = null
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (event is WorldEvent.Change) return true
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Water Board"
    }

    private var solutions = HashMap<LeverBlock, Array<Double>>()
    private var patternIdentifier = -1
    private var openedWaterTicks = -1
    private var tickCounter = 0

    override var repositionTicker: Ticker? = null
    private var lastClick = 0L
    private var atChest = false
    private data class StartingBoard(val pos: BlockPos, val levers: Map<LeverBlock, Boolean>)
    private var startingBoard: StartingBoard? = null
    private val playableLevers = listOf(LeverBlock.WATER, LeverBlock.COAL, LeverBlock.GOLD, LeverBlock.QUARTZ,
        LeverBlock.DIAMOND, LeverBlock.EMERALD, LeverBlock.CLAY)
    private val leverStates = mutableMapOf<LeverBlock, Boolean>()
    private var restoring = false
    private var resumeAfterTick = 0
    private var expectedLever: LeverBlock? = null
    private var paused = false
    private var scanNoticeShown = false

    private val solutionList: List<Pair<LeverBlock, Double>>
        get() = solutions
            .flatMap { (lever, times) -> times.drop(lever.i).map { lever to it } }
            .sortedWith(
                compareBy(
                    { it.second != 0.0 },
                    { if (it.second == 0.0) it.first.ordinal else Int.MAX_VALUE },
                    { if (it.second != 0.0) it.second else 0.0 }
                )
            )

    private fun scan(optimized: Boolean) = with (Dungeon.currentRoom) {
        if (this?.name != "Water Board" || isSolved() || patternIdentifier != -1 || paused) return@with
        val currentStates = mutableMapOf<LeverBlock, Boolean>()
        for (lever in playableLevers) {
            val state = lever.leverPos.state
            if (!state.hasProperty(POWERED)) return@with
            currentStates[lever] = state.getValue(POWERED)
        }
        if (leverStates.isEmpty()) leverStates.putAll(currentStates)
        if (restoring) {
            if (expectedLever != null || currentStates != startingBoard?.levers) return@with
            restoring = false
            resumeAfterTick = tickCounter + 40
        }
        if (tickCounter < resumeAfterTick) return@with
        if (currentStates[LeverBlock.WATER] == true) {
            showScanNotice("Close the water lever to restart Water Board.")
            return@with
        }
        val extendedSlots = WoolColour.entries.joinToString("") { if (it.isExtended) it.ordinal.toString() else "" }
        if (extendedSlots.length != 3) {
            if (startingBoard?.pos == clayPos) showScanNotice("Water Board's current gates do not match a starting pattern.")
            return@with
        }

        val pattern = when {
            getRealCoords(BlockPos(14, 77, 27)).state.block == Blocks.TERRACOTTA -> 0 // right block == clay
            getRealCoords(BlockPos(16, 78, 27)).state.block == Blocks.EMERALD_BLOCK -> 1 // left block == emerald
            getRealCoords(BlockPos(14, 78, 27)).state.block == Blocks.DIAMOND_BLOCK -> 2 // right block == diamond
            getRealCoords(BlockPos(14, 78, 27)).state.block == Blocks.QUARTZ_BLOCK  -> 3 // right block == quartz
            else -> {
                showScanNotice("Water Board pattern is not ready.")
                return@with
            }
        }
        val plan = waterSolutions.getAsJsonObject(optimized.toString())?.getAsJsonObject(pattern.toString())
            ?.getAsJsonObject(extendedSlots) ?: return@with
        startingBoard = StartingBoard(clayPos, currentStates)
        leverStates.putAll(currentStates)
        patternIdentifier = pattern
        solutions.clear()
        plan.entrySet().forEach { entry ->
            solutions[
                when (entry.key) {
                    "diamond_block" -> LeverBlock.DIAMOND
                    "emerald_block" -> LeverBlock.EMERALD
                    "hardened_clay" -> LeverBlock.CLAY
                    "quartz_block"  -> LeverBlock.QUARTZ
                    "gold_block"    -> LeverBlock.GOLD
                    "coal_block"    -> LeverBlock.COAL
                    "water"         -> LeverBlock.WATER
                    else -> LeverBlock.NONE
                }
            ] = entry.value.asJsonArray.map { it.asDouble }.toTypedArray()
        }
    }

    private fun reset() {
        LeverBlock.entries.forEach { it.i = 0 }
        patternIdentifier = -1
        solutions.clear()
        openedWaterTicks = -1
        tickCounter = 0

        if (repositionTicker != null) mc.options.keyShift.isDown = false
        repositionTicker = null
        lastClick = 0L
        atChest = false
        leverStates.clear()
        restoring = false
        resumeAfterTick = 0
        expectedLever = null
        paused = false
        scanNoticeShown = false
    }

    private fun observeLever(pos: BlockPos, state: BlockState) {
        if (leverStates.isEmpty() || !state.hasProperty(POWERED)) return
        val lever = playableLevers.firstOrNull { it.leverPos == pos } ?: return
        val powered = state.getValue(POWERED)
        if (leverStates.put(lever, powered) == powered) return
        if (expectedLever == lever) expectedLever = null
        if (restoring || paused || solutions.isEmpty()) return
        val next = solutionList.firstOrNull() ?: return
        if (next.first != lever || !readyAt(next.second) ||
            (lever == LeverBlock.WATER && openedWaterTicks == -1 && !powered)) {
            pause("A lever changed out of sequence. Re-enter the room to rescan.")
            return
        }
        if (lever == LeverBlock.WATER && openedWaterTicks == -1) openedWaterTicks = tickCounter
        lever.i++
    }

    private fun readyAt(time: Double): Boolean =
        time == 0.0 || openedWaterTicks >= 0 && tickCounter >= openedWaterTicks + (time * 20).toInt()

    private fun OdonRoom.isSolved(): Boolean =
        Puzzle.WATER_BOARD.status == PuzzleStatus.Completed || data.state == RoomState.CLEARED || data.state == RoomState.GREEN

    private fun readyForClick(): Boolean {
        if (paused) return false
        if (expectedLever == null) return !restoring || System.currentTimeMillis() - lastClick >= 200L
        if (System.currentTimeMillis() - lastClick >= 2000L) pause("The server did not confirm a lever click. Re-enter the room to rescan.")
        return false
    }

    private fun expectLever(lever: LeverBlock) {
        expectedLever = lever
        lastClick = System.currentTimeMillis()
    }

    private fun requestLever(lever: LeverBlock) {
        expectLever(lever)
        AuraManager.interactBlock(lever.leverPos)
    }

    private fun moveToLever(room: OdonRoom, lever: LeverBlock): Boolean {
        val z = when (lever.relativePosition.z) {
            20, 15 -> lever.relativePosition.z
            10, 5 -> 9
            else -> return false
        }
        val spot = room.getRealCoords(BlockPos(15, 58, z))
        if (atLanding(spot)) return true
        reposition(spot, bow = false, arrived = { atLanding(spot) })
        return false
    }

    private fun atLanding(spot: BlockPos): Boolean =
        player.blockPosition().x == spot.x && player.blockPosition().z == spot.z &&
            kotlin.math.abs(player.y - getEtherwarpLandingY(spot)) <= 0.1

    private fun pause(message: String) {
        paused = true
        ChatUtils.modMessage("§eWater Board: $message")
    }

    private fun showScanNotice(message: String) {
        if (scanNoticeShown) return
        scanNoticeShown = true
        ChatUtils.modMessage("§e$message")
    }

    private fun chest(room: OdonRoom) {
        val spot = room.getRealCoords(BlockPos(15, 58, 22))
        if (!atLanding(spot)) return reposition(spot, bow = false, arrived = { atLanding(spot) })
        mc.options.keyShift.isDown = false
        atChest = true
    }

    private enum class WoolColour(val relativePosition: BlockPos) {
        PURPLE(BlockPos(15, 56, 19)),
        ORANGE(BlockPos(15, 56, 18)),
        BLUE(BlockPos(15, 56, 17)),
        GREEN(BlockPos(15, 56, 16)),
        RED(BlockPos(15, 56, 15));

        inline val isExtended: Boolean get() =
            Dungeon.currentRoom?.getRealCoords(relativePosition)?.state?.isAir == false
    }

    private enum class LeverBlock(val relativePosition: BlockPos, var i: Int = 0) {
        COAL(BlockPos(20, 61, 10)),
        GOLD(BlockPos(20, 61, 15)),
        QUARTZ(BlockPos(20, 61, 20)),
        DIAMOND(BlockPos(10, 61, 20)),
        EMERALD(BlockPos(10, 61, 15)),
        CLAY(BlockPos(10, 61, 10)),
        WATER(BlockPos(15, 60, 5)),
        NONE(BlockPos(0, 0, 0));

        inline val leverPos: BlockPos
            get() = Dungeon.currentRoom?.getRealCoords(relativePosition) ?: BlockPos(0, 0, 0)
    }
}
