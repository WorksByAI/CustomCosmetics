package quoi.api.autoroutes.nodes

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.equalsOneOf
import quoi.utils.getHitResult
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.TeleportUtils.getEtherPos
import quoi.utils.skyblock.item.TeleportUtils.isEtherwarpPassable
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import quoi.utils.skyblock.player.interact.AuraManager

@TypeName("boom")
class BoomNode : RouteNode() {
    var yaw = 0f
    var pitch = 0f

    var target: Vec3? = null

    @Transient
    private var realYaw = 0f

    @Transient
    private var realTarget: Vec3? = null

    @Transient
    private var blastTarget: BlockPos? = null

    @Transient
    private var nearbyWall: List<BlockPos> = emptyList()
    @Transient
    private var blockedWarp: EtherwarpNode? = null
    @Transient
    internal var cleared = false
        private set

    @Transient
    override var actionFailure: String? = null
        private set

    override val colour: Colour
        get() = Colour.RED

    override val priority: Int
        get() = 100

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
        realTarget = target?.let { room.getRealCoords(it) }
    }

    override fun checkAwaits(pos: MutableVec3): Boolean {
        if (this.pos.distanceToSqr(player.position()) > 0.1) return false
        return super.checkAwaits(pos)
    }

    override fun reset() {
        super.reset()
        blastTarget = null
        nearbyWall = emptyList()
        blockedWarp = null
        cleared = false
        actionFailure = null
    }

    internal fun blastOpened(): Boolean {
        val clicked = blastTarget ?: return false
        return (level.isLoaded(clicked) && level.getBlockState(clicked).isAir)
            || nearbyWall.any { level.isLoaded(it) && isEtherwarpPassable(level, it) }
            || blockedWarp?.reachableFrom(player.position()) == true
    }

    override fun execute(pos: MutableVec3): Boolean {
        if (this.pos.distanceToSqr(player.position()) > 0.1) {
            actionFailure = "Move closer to the Superboom TNT step"
            return false
        }
        val previous = blastTarget.takeIf { AutoRoutes.stallRecovery }
        if (previous != null) {
            if (!level.isLoaded(previous)) {
                actionFailure = "Superboom TNT target is not loaded"
                return false
            }
            if (blastOpened()) {
                cleared = true
                actionFailure = null
                return true
            }
            actionFailure = "Superboom TNT passage change was not confirmed"
            return false
        }
        val target = realTarget
        val block = if (target != null) { // rsa convert compat
            traverseVoxels(pos.immutable().addVec(y = getEyeHeight(true)), target, true).pos
        } else {
            pos.immutable().addVec(y = getEyeHeight(true)).getEtherPos(realYaw, pitch, 6.0).pos
        } ?: return true

        if (!player.mainHandItem.skyblockId.equalsOneOf("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
            val swap = SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT", warnIfMissing = !AutoRoutes.stallRecovery)
            if (!swap.success) {
                actionFailure = if (swap == SwapResult.NOT_FOUND) "Superboom TNT is missing from the hotbar" else "Could not select Superboom TNT"
                return swap == SwapResult.NOT_FOUND && !AutoRoutes.stallRecovery
            }
        }
        if (AutoRoutes.stallRecovery) {
            val hit = block.getHitResult()
            if (hit == null) {
                actionFailure = "Could not aim at the Superboom TNT placement"
                return false
            }
            val placement = block.relative(hit.direction)
            val neighbors = Direction.values().map { placement.relative(it) } + placement
            val crackedWall = BlockPos.betweenClosed(placement.offset(-2, -2, -2), placement.offset(2, 2, 2))
                .asSequence()
                .filter { level.isLoaded(it) && level.getBlockState(it).`is`(Blocks.CRACKED_STONE_BRICKS) }
                .map { it.immutable() }.toList()
            nearbyWall = (neighbors + crackedWall).distinct().filter {
                it != block && level.isLoaded(it) && !isEtherwarpPassable(level, it)
            }
            val origin = player.position()
            blockedWarp = AutoRoutes.nextLocalEtherwarp(this, origin)?.takeUnless { it.reachableFrom(origin) }
            blastTarget = block
            AuraManager.interactBlock(block)
            actionFailure = "Superboom TNT passage change was not confirmed"
            return false
        }
        AuraManager.interactBlock(block)
        actionFailure = null
        return true
    }

    override fun create(room: OdonRoom): RouteNode? {
        player.eyePosition(true).getEtherPos(player.yaw, player.pitch, 6.0).pos ?: return null
        yaw = room.getRelativeYaw(player.yaw)
        pitch = player.pitch
        return this
    }
}
