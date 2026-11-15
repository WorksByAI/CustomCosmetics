package quoi.api.autoroutes.nodes

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.TeleportUtils.getEtherPos
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
    private var blastSent = false

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
        blastSent = false
        actionFailure = null
    }

    internal fun blastOpened(): Boolean = blastTarget?.let { level.isLoaded(it) && level.getBlockState(it).isAir } == true

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
            if (level.getBlockState(previous).isAir) {
                actionFailure = null
                return true
            }
            if (blastSent) {
                actionFailure = "Superboom TNT has not opened the route"
                return false
            }
        }
        val target = realTarget
        val block = previous ?: if (target != null) { // rsa convert compat
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
        AuraManager.interactBlock(block)
        if (AutoRoutes.stallRecovery) {
            blastTarget = block
            blastSent = true
            actionFailure = "Superboom TNT has not opened the route"
            return false
        }
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
