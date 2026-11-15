package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.logger
import quoi.api.autoroutes.AutoRoutesCommand
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.RouteRegistry
import quoi.api.autoroutes.awaits.RaycastAwait
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.BoomNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.autoroutes.nodes.UseItemNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.Puzzle
import quoi.api.skyblock.dungeon.enums.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.skyblock.item.ItemUtils.extraAttributesView
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager

object AutoRoutes : Module(
    "Auto Routes",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    val zeroTickDb by switch("Zero tick dungeon breaker")
    private val pauseOnMimic by switch("Pause on mimic", true, desc = "Pauses the route and shows a title when opening a Mimic chest.")
    internal val stallRecovery by switch("Stall Recovery", desc = "Show why a route stalls.")
    private val skipToWarpKey by keybind("Skip to Next Etherwarp", desc = "Skip the current action and attempt the next Etherwarp in the route.")
        .onPress { if (quoi.compat.GameAccess.screen() == null) skipToNextEtherwarp() }
    private val devMode by switch("Dev Mode", desc = "Write route starts, waits, failures and retries to latest.log.")
    private val etherwarpOnlyStart by switch("Etherwarp Only Start", desc = "Start a route only after a server-confirmed Etherwarp landing on a route node.")
    internal val watchingStalls: Boolean get() = stallRecovery || devMode
    private val style by selector("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)

    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { style.selected == "Filled box" && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE)

    private val colourDropdown by text("Colours").visibleIf { multicolour }
    private val colours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour).childOf(::colourDropdown)
        this.register(set)
        type to set
    }

    private val fillColourDropdown by text("Fill colours").visibleIf { style.selected == "Filled box" && multicolour }
    private val fillColours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour.withAlpha(0.5f), allowAlpha = true).json("$type fill").childOf(::fillColourDropdown)
        this.register(set)
        type to set
    }

    var executing = false
        private set
    val routing: Boolean
        get() = !editMode && (executing || pendingInteract != null) && active
    internal val waitingForBatDeath: Boolean
        get() = active && activeNode?.let { it.hasSecretAwait && (stallRecovery || it is UseItemNode && it.isBatAttack) } == true
    var editMode = false
        set(value) {
            if (field != value) {
                resetExecution()
                clearEntryDetection()
            }
            field = value
        }
    var breakerRing: BreakerNode? = null
    var currentChain: String? = null

    val routeNodes: ConfigMap<String, MutableList<RouteNode>> by configMap("route_nodes.json")
    private var activeRoom: OdonRoom? = null
    private var roomNodes: List<RouteNode> = emptyList()
    private data class PendingInteract(val direction: Direction, val slot: Int, val node: RouteNode?,
                                       var warpTarget: Vec3? = null, var origin: Vec3? = null)
    private data class WarpAttempt(val node: RouteNode, val target: Vec3, val sentAt: Long, val origin: Vec3?)
    private data class Continuation(val warp: EtherwarpNode, val origin: Vec3)
    private enum class Stop { SECRET, CONDITION, PUZZLE, PUZZLE_FAILED, MIMIC, ACTION, SLOT, WARP_ARRIVAL }
    private class Stall(val node: RouteNode, val stop: Stop, val since: Long, val origin: Vec3?) {
        var reported = false
    }
    private class RouteCast(val origin: Vec3, val target: Vec3? = null, val expiresAt: Long = System.nanoTime() + 3_000_000_000L) {
        var arrival: Vec3? = null
    }
    private val stallTimeoutNanos = 3_000_000_000L
    private var pendingInteract: PendingInteract? = null
    private val warpAttempts = ArrayDeque<WarpAttempt>()
    private var stall: Stall? = null
    private var lastReportedStall: Pair<Stall, Vec3>? = null
    private var continuation: Continuation? = null
    private var blockHandoff: RouteNode? = null
    private var devModeWasActive = false
    private var routeCast: RouteCast? = null
    private var routeEntry: RouteNode? = null
    private var entryPending = false
    private var previousEtherwarpOnlyStart = false
    private var position: MutableVec3? = null
    private var interactDelay = 0

    private var activeNode: RouteNode? = null
    private var forcedNode: RouteNode? = null
    private var mimicNode: RouteNode? = null

    private var lastChainName: String? = null
    private var lastChainIndex: Int = -1

    init {
        AutoRoutesCommand.register()

        on<TickEvent.Start> {
            if (etherwarpOnlyStart != previousEtherwarpOnlyStart) {
                resetExecution()
                clearEntryDetection()
                previousEtherwarpOnlyStart = etherwarpOnlyStart
            }
            if (devMode && !devModeWasActive) trace("started (${roomNodes.size} route steps)")
            devModeWasActive = devMode
            if (!watchingStalls && stall?.origin == null) stall = null
            if (!stallRecovery && warpAttempts.isNotEmpty()) warpAttempts.removeIf { it.origin == null }
            if (interactDelay > 0) interactDelay--
            if (editMode || breakerRing != null || roomNodes.isEmpty() || ClearExecutor.routing) {
                if (position != null || activeNode != null || pendingInteract != null || mimicNode != null) resetExecution()
                return@on
            }
            if (etherwarpOnlyStart) {
                acceptRouteLanding()
                if (routeEntry == null) return@on
            }
            mimicNode?.let {
                trackStall(it, Stop.MIMIC)
                return@on
            }

            val stalledWarp = stalledWarp()
            if (stalledWarp != null) {
                trackStall(stalledWarp.node, Stop.WARP_ARRIVAL, stalledWarp.sentAt, stalledWarp.origin)
                return@on
            }

            pendingInteract?.let {
                if (stall?.stop == Stop.SLOT && stall?.reported == true && (stallRecovery || it.origin != null)) return@on
                if (!SwapManager.swapToSlot(it.slot).success) {
                    it.node?.let { node -> trackStall(node, Stop.SLOT, origin = it.origin) }
                    return@on
                }
                val target = it.warpTarget
                val node = it.node
                if ((stallRecovery || it.origin != null) && target != null && node != null) {
                    if (warpAttempts.size == 32) warpAttempts.removeFirst()
                    warpAttempts.addLast(WarpAttempt(node, target, System.nanoTime(), it.origin))
                    if (devMode) trace("sent Etherwarp toward $target", node)
                }
                player.useItem(it.direction)
                pendingInteract = null
                stall = null
            }

            if (activeNode?.hasSecretAwait == true) {
                val requireDeath = waitingForBatDeath
                EntityUtils.getEntities<Bat>(radius = 10.0) { it.isSecretBat() && (!requireDeath || it.isDeadOrDying) }
                    .forEach { EventDispatcher.onSecretBat(it.id) }
            }

            if (position == null) {
                position = player.position().mutable()
            }

            val desynced = hasActiveWarpOrigin()

            if (!desynced) {
                roomNodes.forEach { if (!it.inside() && it.triggered) it.reset() }
                position = player.position().mutable()
            }

            handleQueue(position!!)
        }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket>(Priority.HIGHEST) {
            val cast = routeCast?.takeIf { it.arrival == null && System.nanoTime() < it.expiresAt }
            if (warpAttempts.isEmpty() && cast == null) return@on
            val target = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), packet.change, packet.relatives).position
            val confirmed = warpAttempts.indexOfFirst { reached(it.target, target) }
            repeat(confirmed + 1) { warpAttempts.removeFirst() }
            if (confirmed >= 0 && stall?.stop == Stop.WARP_ARRIVAL) stall = null
            val expected = cast?.target
            if (cast != null && if (expected != null) reached(expected, target) else isCenteredLanding(cast.origin, target)) {
                cast.arrival = target
                if (devMode) trace("Etherwarp position packet landed at $target")
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemPacket>(Priority.HIGHEST) { observeEtherwarpUse(packet.hand) }
        on<PacketEvent.Sent, ServerboundUseItemOnPacket>(Priority.HIGHEST) { observeEtherwarpUse(packet.hand) }

        on<KeyEvent.Input> {
            if (editMode || activeNode == null) return@on
            input.shift = activeNode is EtherwarpNode || activeNode!!.unsneak != true
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state) return@on
            if (skipToWarpKey.key == button - 100 && skipToWarpKey.isModifierDown()) return@on
            if (mimicNode != null && mc.hitResult is EntityHitResult) return@on
            if (stallRecovery && stall != null) return@on
            if (skipNode()) cancel()
        }

        on<DungeonEvent.Secret.Interact> {
            activeNode?.onSecret()
            interactDelay = 2

            if (!pauseOnMimic || editMode || !blockState.`is`(Blocks.TRAPPED_CHEST)) return@on
            if (ScanUtils.getRoomFromPos(blockPos.x, blockPos.z)?.data?.type == RoomType.TRAP) return@on
            mimicNode = activeNode ?: return@on
            pendingInteract = null
            warpAttempts.clear()
            activeNode = null
            forcedNode = null
            position = null
            executing = false
            PlayerUtils.setTitle("§cMimic!", stayAlive = 60)
        }
        on<DungeonEvent.Secret.Item> { activeNode?.onSecret() }
        on<DungeonEvent.Secret.Bat> { activeNode?.onSecret() }

        on<DungeonEvent.Room.Enter> {
            updateCache(room)
        }

        on<RenderEvent.World> {
            roomNodes.forEach { it.render(ctx, style.selected, it.colour(), it.fillColour(), activeCol, thickness) }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawText(string, x, y)
            }
        }

        on<WorldEvent.Change> {
            resetExecution()
            clearEntryDetection()
            activeRoom = null
            roomNodes = emptyList()
        }
    }

    fun skipNode(): Boolean {
        if (!active || editMode || breakerRing != null || ClearExecutor.routing) return false
        if (stallRecovery && stall?.reported == true) return false
        if (pendingInteract != null || continuation != null) return false
        val pausedNode = mimicNode
        val pos = if (pausedNode != null) player.position().mutable() else position ?: player.position().mutable()

        val nodes = (if (pausedNode != null) listOf(pausedNode) else roomNodes)
            .filter { it.inside(pos) && canRunStep(it) }.sortedByDescending { it.priority }
        if (nodes.isEmpty()) return false

        val next = (nodes.indexOf(forcedNode) + 1) % nodes.size
        val node = nodes[next]
        if (activeNode !== node) activeNode?.takeIf { !it.triggered }?.reset()
        mimicNode = null
        forcedNode = node
        activeNode = node
        stall = null
        if (devMode) trace("manual step", node)
        executeNode(node, pos)
        return true
    }

    fun handleQueue(playerPos: MutableVec3) {
        if (mimicNode != null) return

        if (forcedNode?.inside(playerPos) == false) forcedNode = null
        val forced = forcedNode?.takeIf { !it.triggered && it.inside(playerPos) && canRunStep(it) }
        if (continuation?.let { !nearSkipOrigin(it.origin) } == true) continuation = null
        val resume = continuation
        if (blockHandoff?.atPlayer() == false) blockHandoff = null
        val openedHere = blockHandoff != null
        val entry = routeEntry?.takeIf { etherwarpOnlyStart && entryPending && it.matchesLanding(playerPos) }
        val node = resume?.warp ?: forced ?: entry ?: roomNodes.asSequence()
            .filter { !it.triggered && canRunStep(it) && (it.inside(playerPos) ||
                openedHere && it is EtherwarpNode && it.matchesLanding(playerPos)) }
            .filter { n ->
                val c = n.chain ?: return@filter true
                if (c.index == 0) return@filter true
                c.name == lastChainName && c.index == lastChainIndex + 1
            }
            .maxByOrNull { it.priority } ?: run {

            activeNode?.takeIf { !it.triggered }?.reset()
            activeNode = null
            executing = false
            stall = null

            if (!hasActiveWarpOrigin() && lastReportedStall?.let { nearSkipOrigin(it.second) } != true) {
                position = null
                routeEntry = null
                entryPending = false
            }
            return
        }

        if (activeNode != node) {
            activeNode?.takeIf { !it.triggered }?.reset()
            activeNode = node
            executing = false
            stall = null
            lastReportedStall = null
            if (devMode) trace("entered step", node)
        }

        if (interactDelay > 0) return

        val afterBlockChange = openedHere && node is EtherwarpNode
        val executionPos = if (resume == null && !afterBlockChange) playerPos else player.position().mutable()
        if (stallRecovery && activeRoom?.isFullyCollected() == true) satisfySecretWaits(node)
        if (resume == null && forced == null && !executing && !node.checkAwaits(executionPos)) {
            if (watchingStalls) {
                val wait = ticTacToeWait(node) ?:
                    if (node.awaits?.any { it is SecretAwait && it.current < it.amount } == true) Stop.SECRET else Stop.CONDITION
                trackStall(node, wait)
            }
            return
        }
        if (stallRecovery && stall?.stop == Stop.ACTION && stall?.reported == true && node !is EtherwarpNode) {
            val progressed = when (node) {
                is BreakerNode -> node.hasProgressSinceStall()
                is BoomNode -> node.blastOpened()
                else -> false
            }
            if (!progressed) return
            stall = null
        }
        executeNode(node, executionPos, resume)
    }

    fun queueInteract(dir: Direction) {
        pendingInteract = PendingInteract(dir, player.inventory.selectedSlot, activeNode)
    }

    private fun executeNode(node: RouteNode, pos: MutableVec3, resume: Continuation? = null) {
        if (!executing && devMode) trace("executing", node)
        executing = true
        val completed = node.execute(pos)
        if (!completed) {
            node.triggered = false
            position = null
            val stop = if (resume == null && node is EtherwarpNode && node.pathBlocked) ticTacToeWait(node) else null
            trackStall(node, stop ?: Stop.ACTION, origin = resume?.origin)
            return
        }
        if (devMode) node.actionFailure?.let { trace(it, node) }
        if ((stallRecovery || resume != null) && node is EtherwarpNode) pendingInteract?.let {
            it.warpTarget = pos.immutable()
            it.origin = resume?.origin
        }
        position = pos
        finishNode(node)
    }

    private fun finishNode(node: RouteNode) {
        node.triggered = true
        if (node === routeEntry) entryPending = false
        if (node === continuation?.warp) continuation = null
        if (node is EtherwarpNode) blockHandoff = null
        if (node is BreakerNode && node.isCleared() || node is BoomNode && node.cleared) {
            blockHandoff = node
            position = null
        }
        node.chain?.let {
            lastChainName = it.name
            lastChainIndex = it.index
        }
        executing = false
        stall = null
        lastReportedStall = null
        if (devMode) trace("completed", node)
    }

    private fun trackStall(node: RouteNode, stop: Stop, since: Long? = null, origin: Vec3? = null) {
        if (!watchingStalls && origin == null) return
        var wait = stall
        if (wait == null || wait.node !== node || wait.stop != stop) {
            wait = Stall(node, stop, since ?: System.nanoTime(), origin)
            stall = wait
            if (devMode) trace(if (stop == Stop.PUZZLE) "waiting for Tic Tac Toe to open the passage" else "waiting ($stop)", node)
        }
        if (stop == Stop.PUZZLE) return
        if (wait.reported || System.nanoTime() - wait.since < stallTimeoutNanos || !node.atPlayer(wait.origin)) return
        wait.reported = true
        lastReportedStall = wait to player.position()
        val reason = when (stop) {
            Stop.SECRET -> "This route step is waiting for a secret event"
            Stop.CONDITION -> if (node is EtherwarpNode && node.awaits?.any { it is RaycastAwait } == true) {
                "Etherwarp path or landing is blocked"
            } else "Route step requirements not met (${node.typeName})"
            Stop.PUZZLE -> return
            Stop.PUZZLE_FAILED -> "Tic Tac Toe failed and the passage is still closed"
            Stop.ACTION -> node.actionFailure ?: "Route action did not finish (${node.typeName})"
            Stop.MIMIC -> if (Dungeon.mimicKilled) "Mimic pause" else "Mimic not dead (kill not confirmed)"
            Stop.SLOT -> "Could not switch to the route's hotbar slot"
            Stop.WARP_ARRIVAL -> "Etherwarp failed (no arrival confirmed)"
        }
        if (devMode) trace(reason, node)
        if (stallRecovery || wait.origin != null) modMessage("&eAuto Routes: &c$reason.")
    }

    private fun skipToNextEtherwarp() {
        if (!active || editMode || breakerRing != null || ClearExecutor.routing) return
        val wait = stall?.takeIf { it.node.atPlayer(it.origin) }
            ?: lastReportedStall?.takeIf { nearSkipOrigin(it.second) && (!etherwarpOnlyStart || routeEntry != null) }?.first
        val current = wait?.node ?: mimicNode ?: pendingInteract?.node ?: activeNode
        if (current == null || wait == null && !current.atPlayer(continuation?.origin)) {
            modMessage("&eAuto Routes: No active route step to skip.")
            return
        }
        val waitingOnSecret = wait?.stop == Stop.SECRET || mimicNode === current ||
            (wait == null && !current.triggered && !executing &&
                current.awaits?.any { it is SecretAwait && it.current < it.amount } == true)
        // A secret check on an Etherwarp node happens before that hop is attempted.
        val rest = successors(current).toList()
        val origin = player.position()
        val warp = if (current is EtherwarpNode && waitingOnSecret) current else
            (if (current.chain == null) nextLocalEtherwarp(current, origin) else null)
                ?: rest.filterIsInstance<EtherwarpNode>().firstOrNull { !it.triggered || wait?.stop == Stop.WARP_ARRIVAL }
        if (warp == null) {
            modMessage("&eAuto Routes: No next Etherwarp in this route.")
            return
        }
        pendingInteract = null
        warpAttempts.clear()
        forcedNode = null
        mimicNode = null
        if (current !== warp) {
            markSkipped(current)
            val index = rest.indexOf(warp)
            if (index >= 0) rest.take(index).forEach(::markSkipped)
        }
        if (wait?.stop == Stop.WARP_ARRIVAL) {
            rest.dropWhile { it !== warp }.forEach { it.reset() }
        }
        activeNode = warp
        continuation = Continuation(warp, origin)
        position = origin.mutable()
        executing = false
        stall = null
        lastReportedStall = null
        if (devMode) trace("skipping from ${current.typeName} (${wait?.stop})", warp)
    }

    private fun successors(node: RouteNode): Sequence<RouteNode> {
        val chain = node.chain
        if (chain != null) {
            return roomNodes.asSequence().filter { it.chain?.name == chain.name && it.chain!!.index > chain.index }
                .sortedBy { it.chain!!.index }
        }
        val index = roomNodes.indexOf(node)
        if (index < 0) return emptySequence()
        return roomNodes.asSequence().drop(index + 1).takeWhile { it.start != true }.filter { it.chain == null }
    }

    internal fun nextLocalEtherwarp(node: RouteNode, origin: Vec3): EtherwarpNode? {
        val live = origin.mutable()
        val recorded = node.pos.mutable()
        fun matches(warp: EtherwarpNode) = warp !== node && !warp.triggered &&
            (warp.matchesLanding(live) || warp.matchesLanding(recorded))
        val next = successors(node).filterIsInstance<EtherwarpNode>().firstOrNull(::matches)
        if (next != null || node.chain != null) return next
        return roomNodes.asSequence().filterIsInstance<EtherwarpNode>()
            .firstOrNull { it.chain == null && it.start != true && matches(it) }
    }

    private fun markSkipped(node: RouteNode) {
        node.reset()
        node.triggered = true
        if (node === routeEntry) entryPending = false
    }

    private fun hasActiveWarpOrigin(): Boolean =
        roomNodes.any { it.inside() && it.triggered } ||
            warpAttempts.lastOrNull()?.origin?.let { reached(it, player.position()) } == true

    private fun stalledWarp(): WarpAttempt? {
        val first = warpAttempts.firstOrNull() ?: return null
        val now = System.nanoTime()
        if (now - first.sentAt < stallTimeoutNanos) return null
        val stalled = warpAttempts.firstOrNull { now - it.sentAt >= stallTimeoutNanos && it.node.atPlayer(it.origin) }
        if (stalled == null) {
            while (warpAttempts.firstOrNull()?.let { now - it.sentAt >= stallTimeoutNanos } == true) warpAttempts.removeFirst()
            if (stall?.stop == Stop.WARP_ARRIVAL) stall = null
        }
        return stalled
    }

    private fun satisfySecretWaits(node: RouteNode) {
        node.awaits?.forEach { if (it is SecretAwait) it.current = maxOf(it.current, it.amount) }
    }

    private fun ticTacToeWait(node: RouteNode): Stop? {
        val room = activeRoom ?: return null
        if (room.name != Puzzle.TTT.displayName || node !is EtherwarpNode ||
            node.awaits?.any { it is RaycastAwait } != true) return null
        return when {
            Puzzle.TTT.status == PuzzleStatus.Failed || room.data.state == RoomState.FAILED -> Stop.PUZZLE_FAILED
            Puzzle.TTT.status == PuzzleStatus.Completed || room.data.state.equalsOneOf(RoomState.CLEARED, RoomState.GREEN) -> null
            else -> Stop.PUZZLE
        }
    }

    private fun OdonRoom.isFullyCollected(): Boolean =
        identified && (data.secrets == 0 || (secretsFound ?: -1) >= data.secrets)

    private fun RouteNode.atPlayer(origin: Vec3? = null) =
        if (origin == null) inside() || matchesLanding(player.position().mutable()) else nearSkipOrigin(origin)

    private fun nearSkipOrigin(origin: Vec3) = player.position().distanceToSqr(origin) <= 2.25

    private fun RouteNode.matchesLanding(landing: MutableVec3): Boolean {
        if (inside(landing)) return true
        return kotlin.math.abs(pos.y - landing.y) <= 0.5 &&
            kotlin.math.floor(pos.x) == kotlin.math.floor(landing.x) &&
            kotlin.math.floor(pos.z) == kotlin.math.floor(landing.z)
    }

    private fun observeEtherwarpUse(hand: InteractionHand) {
        if (!etherwarpOnlyStart || editMode || breakerRing != null || pendingInteract != null || ClearExecutor.routing) return
        val held = player.getItemInHand(hand)
        val id = held.skyblockId
        if (id != "ETHERWARP_CONDUIT") {
            if (id != "ASPECT_OF_THE_VOID" && id != "ASPECT_OF_THE_END") return
            if ((!player.lastSentInput.shift && !player.isShiftKeyDown) ||
                held.extraAttributesView?.getBoolean("ethermerge")?.orElse(false) != true) return
        }
        routeCast = RouteCast(player.position())
        if (devMode) trace("Etherwarp item used ($id)")
    }

    private fun isCenteredLanding(origin: Vec3, target: Vec3): Boolean =
        origin.distanceToSqr(target) > 0.09 &&
            kotlin.math.abs(target.x - kotlin.math.floor(target.x) - 0.5) <= 0.125 &&
            kotlin.math.abs(target.z - kotlin.math.floor(target.z) - 0.5) <= 0.125

    internal fun stepProgress(node: RouteNode) {
        if (stall?.node === node && stall?.stop == Stop.ACTION) stall = null
    }

    internal fun expectMapEtherwarp(target: MutableVec3) {
        if (!etherwarpOnlyStart || !active || editMode || breakerRing != null) return
        routeCast = RouteCast(player.position(), target.immutable())
        if (devMode) trace("Interactive Map final Etherwarp toward ${routeCast?.target}")
    }

    private fun acceptRouteLanding() {
        val cast = routeCast ?: return
        if (System.nanoTime() >= cast.expiresAt) {
            routeCast = null
            return
        }
        val arrived = cast.arrival ?: return
        if (!reached(arrived, player.position())) {
            routeCast = null
            return
        }
        val landing = arrived.mutable()
        val current = player.position().mutable()
        val entry = roomNodes.asSequence()
            .filter { (it.chain?.index ?: 0) == 0 && it.matchesLanding(landing) && it.matchesLanding(current) }
            .maxWithOrNull(compareBy<RouteNode> { it.start == true }.thenBy { it.priority }) ?: return
        routeCast = null
        resetExecution()
        routeEntry = entry
        entryPending = true
        position = current
        if (devMode) trace("started from confirmed Etherwarp arrival", entry)
    }

    private fun canRunStep(node: RouteNode): Boolean {
        if (!etherwarpOnlyStart) return true
        val entry = routeEntry ?: return false
        if (node === entry) return entryPending
        return node.start != true && node.chain?.index != 0
    }

    private fun reached(target: Vec3, actual: Vec3): Boolean {
        val block = BlockPos.containing(target)
        return actual.x >= block.x && actual.x < block.x + 1 &&
            actual.z >= block.z && actual.z < block.z + 1 && kotlin.math.abs(actual.y - target.y) < 1.0
    }

    private fun clearEntryDetection() {
        routeCast = null
    }

    private fun trace(message: String, node: RouteNode? = null) {
        val step = node?.let { " [${it.typeName}, relative=${it.relative}, chain=${it.chain}]" }.orEmpty()
        logger.info("Auto Routes: {}{}", message, step)
    }

    private fun resetExecution() {
        roomNodes.forEach { it.reset() }
        pendingInteract = null
        warpAttempts.clear()
        stall = null
        lastReportedStall = null
        continuation = null
        blockHandoff = null
        routeEntry = null
        entryPending = false
        activeNode = null
        forcedNode = null
        mimicNode = null
        position = null
        executing = false
        interactDelay = 0
        lastChainName = null
        lastChainIndex = -1
    }

    override fun onEnable() {
        previousEtherwarpOnlyStart = etherwarpOnlyStart
        updateCache(Dungeon.currentRoom)
        if (devMode) trace("enabled")
    }

    override fun onDisable() {
        if (devMode) trace("disabled")
        devModeWasActive = false
        resetExecution()
        clearEntryDetection()
        activeRoom = null
        roomNodes = emptyList()
        AutoRoutesCommand.stopEditing()
    }

    fun updateCache(room: OdonRoom?) {
        resetExecution()
        activeRoom = room
        roomNodes = room?.let { routeNodes[it.name]?.toList() }.orEmpty()
        if (room != null) roomNodes.forEach { it.update(room) }
        if (devMode) trace("room=${room?.name}, steps=${roomNodes.size}")
    }

    private val RouteNode.hasSecretAwait: Boolean
        get() = this.awaits?.any { it is SecretAwait } == true

    private fun Bat.isSecretBat() = maxHealth.equalsOneOf(100f, 200f, 400f, 800f)

    private fun RouteNode.colour() = if (multicolour) colours[this.typeName]?.value ?: Colour.WHITE else AutoRoutes.colour
    private fun RouteNode.fillColour() = if (multicolour) fillColours[this.typeName]?.value ?: Colour.WHITE else fillColour
}
