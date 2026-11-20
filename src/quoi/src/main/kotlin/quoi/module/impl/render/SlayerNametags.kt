package quoi.module.impl.render

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.EntityEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.renderPos
import quoi.utils.render.drawText

object SlayerNametags : Module("Slayer Nametags", desc = "Adjusts the size and position of Slayer boss nameplates.") {
    private val position by selector("Position", "Top", listOf("Bottom", "Middle", "Top"))
    private val sizing by selector("Text size", "Manual", listOf("Manual", "Dynamic"))
    private val scale by slider("Text scale", 1f, 0.5f, 10f, 0.1f).visibleIf { sizing.selected == "Manual" }
    private val names = setOf("Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Conjoined Brood",
        "Sven Packmaster", "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend")
    private val stands = mutableSetOf<ArmorStand>()
    private val replacements = mutableMapOf<ArmorStand, LivingEntity>()

    init {
        on<WorldEvent.Change> { clear() }
        on<EntityEvent.Despawn> {
            if (entity is ArmorStand) stands.remove(entity)
            replacements.entries.removeIf { it.key === entity || it.value === entity }
        }
        on<PacketEvent.ReceivedPost, ClientboundSetEntityDataPacket> {
            if (!Location.inSkyblock) return@on
            val stand = getEntity(packet.id) as? ArmorStand ?: return@on
            if (isSlayerTag(stand)) stands.add(stand)
            else {
                stands.remove(stand)
                replacements.remove(stand)
            }
        }
        on<RenderEvent.Entity> {
            if (Location.inSkyblock && entity is ArmorStand && replacements[entity]?.isAlive == true) cancel()
        }
        on<RenderEvent.World> {
            if (!Location.inSkyblock || stands.isEmpty()) return@on
            for (stand in stands) {
                val name = stand.customName ?: continue
                val boss = replacements[stand]?.takeIf { it.isAlive && it.distanceToSqr(stand) <= 16.0 }
                    ?: stand.level().getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.2, 3.0, 1.2)) {
                        it !is ArmorStand && it !is net.minecraft.world.entity.player.Player && it.isAlive
                    }.minByOrNull { it.distanceToSqr(stand) }
                if (boss == null) {
                    replacements.remove(stand)
                    continue
                }
                val camera = quoi.compat.GameAccess.camera().position()
                if (camera.distanceToSqr(boss.position()) > 64.0 * 64.0) continue
                val offset = when (position.selected) {
                    "Bottom" -> 0.5
                    "Middle" -> boss.bbHeight / 2.0
                    else -> boss.bbHeight + 0.25
                }
                val pos = boss.renderPos.add(0.0, offset, 0.0)
                val size = if (sizing.selected == "Dynamic") (camera.distanceTo(pos) / 3.0).toFloat().coerceIn(0.5f, 10f) else scale
                replacements[stand] = boss
                ctx.drawText(name, pos, scale = size, depth = true)
            }
        }
    }

    override fun onEnable() {
        if (Location.inSkyblock) mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()
            ?.filterTo(stands, ::isSlayerTag)
    }

    override fun onDisable() = clear()

    private fun isSlayerTag(stand: ArmorStand): Boolean {
        val label = stand.customName?.string ?: return false
        return stand.isInvisible && names.any { label.contains(it, ignoreCase = true) }
    }

    private fun clear() {
        stands.clear()
        replacements.clear()
    }
}
