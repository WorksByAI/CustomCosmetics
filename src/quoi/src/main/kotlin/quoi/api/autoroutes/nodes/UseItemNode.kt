package quoi.api.autoroutes.nodes

import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.item.ItemUtils.registryPath
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.SwapManager

@TypeName("use_item")
class UseItemNode : RouteNode() {
    var yaw = 0f
    var pitch = 0f
    var item = ""
    var batAttack: Boolean? = null

    @Transient
    var realYaw = 0f

    @Transient
    private var itemMissing = false

    @Transient
    private var waitingForSlot = false

    @Transient
    private var batShotQueued = false

    internal val isBatAttack: Boolean
        get() = batAttack ?: (item.equalsOneOf("BAT_WAND", "STARRED_BAT_WAND") || legacyRsaBatNode)

    // Earlier RSA imports used a sneaking Hyperion action plus one awaited secret for a bat.
    private val legacyRsaBatNode: Boolean
        get() = item == "HYPERION" && unsneak != true && (awaits?.singleOrNull() as? SecretAwait)?.amount == 1

    override val actionFailure: String?
        get() = when {
            itemMissing -> "Item $item not found in the hotbar"
            waitingForSlot -> "Could not select $item in the hotbar"
            batShotQueued -> "Bat death has not been detected after the cast"
            else -> null
        }

    override val colour: Colour
        get() = Colour.BROWN

    override val priority: Int
        get() = 50

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
    }

    override fun reset() {
        super.reset()
        batShotQueued = false
        itemMissing = false
        waitingForSlot = false
    }

    override fun checkAwaits(pos: MutableVec3): Boolean {
        if (!isBatAttack) return super.checkAwaits(pos)
        return awaits?.all { it is SecretAwait || it.check(pos, this) } ?: true
    }

    override fun execute(pos: MutableVec3): Boolean {
        val waitForBat = isBatAttack && awaits?.any { it is SecretAwait } == true
        if (waitForBat) {
            val secretReady = awaits?.all { it !is SecretAwait || it.check(pos, this) } != false
            if (secretReady) {
                batShotQueued = false
                return true
            }
            if (batShotQueued) return false
        }
        fun matchesItem(slot: Int): Boolean {
            val stack = player.inventory.getItem(slot)
            return item.equalsOneOf(stack.skyblockId, stack.registryPath) ||
                batAttack == true && stack.skyblockId == "HYPERION"
        }
        fun findSlot(matches: (Int) -> Boolean) =
            player.inventory.selectedSlot.takeIf(matches) ?: (0..8).firstOrNull(matches)
        val wandSlot = if (isBatAttack) findSlot {
            player.inventory.getItem(it).skyblockId.equalsOneOf("BAT_WAND", "STARRED_BAT_WAND")
        } else null
        val slot = wandSlot ?: findSlot(::matchesItem)

        itemMissing = slot == null
        waitingForSlot = false
        if (slot == null) {
            if (AutoRoutes.stallRecovery) return false
            modMessage("&cItem &e$item&c not found!")
            return true
        }

        if (player.inventory.selectedSlot != slot) {
            waitingForSlot = !SwapManager.swapToSlot(slot).success
            if (waitingForSlot) return false
        }

        AutoRoutes.queueInteract(Direction(realYaw, pitch))
        batShotQueued = waitForBat
        return !waitForBat
    }

    override fun create(room: OdonRoom): RouteNode? {
        val stack = player.mainHandItem
        if (stack.isEmpty) return null

        yaw = room.getRelativeYaw(player.yaw)
        pitch = player.pitch
        item = stack.skyblockId ?: stack.registryPath
        return this
    }
}
