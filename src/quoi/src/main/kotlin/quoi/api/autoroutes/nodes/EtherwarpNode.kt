package quoi.api.autoroutes.nodes

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.api.world.RaycastResult
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.getDirection
import quoi.utils.getLook
import quoi.utils.getVisiblePoint
import quoi.utils.rayCastVec
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpLandingY
import quoi.utils.skyblock.item.TeleportUtils.isValidEtherwarpBlock
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult

@TypeName("etherwarp")
class EtherwarpNode : RouteNode() {
    var target: Vec3 = Vec3.ZERO

    var yaw: Float? = null // legacy compat
    var pitch: Float? = null

    @Transient
    lateinit var realTarget: Vec3
        private set

    @Transient
    var realYaw: Float? = null

    @Transient
    override var actionFailure: String? = null
        private set

    @Transient
    private var alternativeAim: Vec3? = null

    @Transient
    private var nextAimSearch = 0L

    internal val pathBlocked: Boolean get() = actionFailure == "Etherwarp path or landing is blocked"
    override val colour: Colour
        get() = Colour.CYAN

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = null

        if (yaw != null && pitch != null) {
            realYaw = room.getRealYaw(yaw!!)
            val from = pos.addVec(y = getEyeHeight(true))
            val to = getLook(realYaw!!, pitch!!).scale(61.0)

            realTarget = rayCastVec(from, to).vec
            return
        }

        realTarget = room.getRealCoords(target)
    }

    override fun reset() {
        super.reset()
        alternativeAim = null
        nextAimSearch = 0L
    }

    override fun execute(pos: MutableVec3): Boolean = execute(pos, false)

    internal fun executeFromHere(pos: MutableVec3): Boolean = execute(pos, true)

    private fun execute(pos: MutableVec3, aimFromHere: Boolean): Boolean {
        actionFailure = "Etherwarp failed (sneak input not ready)"
        if (!player.lastSentInput.shift) return false

        val from = pos.add(y = getEyeHeight(true)).immutable()
        val ether = aim(from, aimFromHere)
        actionFailure = "Etherwarp path or landing is blocked"
        if (!ether.succeeded || ether.pos == null) return false

        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
            val swap = SwapManager.swapById("ASPECT_OF_THE_VOID", warnIfMissing = !AutoRoutes.stallRecovery)
            if (!swap.success) {
                actionFailure = if (swap == SwapResult.NOT_FOUND) "Etherwarp failed (Aspect of the Void missing)" else "Etherwarp failed (could not select Aspect of the Void)"
                return swap == SwapResult.NOT_FOUND && !AutoRoutes.stallRecovery
            }
        }

        actionFailure = null
        val dir = getDirection(from, ether.vec)

        AutoRoutes.queueInteract(dir)

        pos.x = ether.pos.x + 0.5
        pos.y = getEtherwarpLandingY(ether.pos) + 0.05
        pos.z = ether.pos.z + 0.5
        return true
    }

    private fun targetFrom(from: Vec3): Vec3 = realYaw?.let {
        rayCastVec(from, getLook(it, pitch!!).scale(61.0)).vec
    } ?: realTarget

    fun raycast(pos: MutableVec3): RaycastResult {
        val from = pos.add(y = getEyeHeight(true)).immutable()
        return aim(from, false)
    }

    private fun aim(from: Vec3, useRecordedTarget: Boolean): RaycastResult {
        val target = if (useRecordedTarget) realTarget else targetFrom(from)
        val recordedEye = pos.addVec(y = getEyeHeight(true))
        val destination = if (useRecordedTarget || realYaw == null) targetBlock(recordedEye, realTarget)
            else targetBlock(from, target)
        val exact = raycast(from, target, destination)
        if (exact.succeeded) return exact.apply { vec = target }

        alternativeAim?.let { alternate ->
            val cached = raycast(from, alternate, destination)
            if (cached.succeeded) return cached.apply { vec = alternate }
            alternativeAim = null
        }
        val now = System.nanoTime()
        if (now < nextAimSearch || from.distanceToSqr(Vec3.atCenterOf(destination)) > 62.0 * 62.0 ||
            !level.isLoaded(destination) || !isValidEtherwarpBlock(level, destination)) return exact
        nextAimSearch = now + 150_000_000L
        val center = Vec3.atCenterOf(destination)
        val centerHit = raycast(from, center, destination)
        if (centerHit.succeeded) {
            alternativeAim = center
            return centerHit.apply { vec = center }
        }
        val visible = getVisiblePoint(from, destination) { offset ->
            val candidate = raycast(from, from.add(offset), destination)
            if (candidate.succeeded) candidate.pos else null
        } ?: return exact
        alternativeAim = visible
        return raycast(from, visible, destination).apply { vec = visible }
    }

    private fun targetBlock(from: Vec3, target: Vec3): BlockPos =
        BlockPos.containing(target.add(target.subtract(from).normalize().scale(0.001)))

    private fun raycast(from: Vec3, target: Vec3, expected: BlockPos): RaycastResult {
        if (from.distanceToSqr(target) > 61.0 * 61.0) return RaycastResult.NONE
        val to = target.add(target.subtract(from).normalize().scale(0.001))
        val hit = traverseVoxels(from, to, true)
        return if (hit.pos == expected) hit else RaycastResult.NONE
    }

    override fun create(room: OdonRoom): RouteNode? {
        val res = rayCastVec()
        if (!res.succeeded) return null
        val vec = res.vec
        val rel = room.getRelativeCoords(vec)
        target = rel
        return this
    }

    override fun render(ctx: LevelRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        val col = if (this.chain != null) Colour.GREEN else Colour.WHITE
        val final = if (yaw != null) Colour.ORANGE else col

        val inside = inside()
        val actualFinal = if (inside) Colour.CYAN else final
        val thickness = if (inside) 3f else 1.5f
        ctx.drawLine(listOf(pos, realTarget), actualFinal, depth = true, thickness = thickness)
    }
}
