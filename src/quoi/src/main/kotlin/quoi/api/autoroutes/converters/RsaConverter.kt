package quoi.api.autoroutes.converters

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.RouteAwait
import quoi.api.autoroutes.awaits.RaycastAwait
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BoomNode
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.autoroutes.nodes.UseItemNode
import quoi.config.ConfigSystem.gson
import quoi.utils.addVec
import quoi.utils.toDirection
import java.io.File

object RsaConverter {
    fun convert(file: File): Map<String, List<RouteNode>> =
        convert(file.reader(Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject)

    fun convert(rooms: JsonObject): Map<String, List<RouteNode>> = rooms.entrySet().associate { (room, rings) ->
        if (!rings.isJsonArray) throw JsonParseException("Expected an RSA route array for $room")
        room to rings.asJsonArray.mapIndexed { index, entry ->
            try {
                readNode(entry.asJsonObject)
            } catch (e: RuntimeException) {
                throw JsonParseException("RSA route $room, node ${index + 1}: ${e.message}", e)
            }
        }
    }

    private fun readNode(ring: JsonObject): RouteNode {
        val type = ring.get("type").asString
        val node = when (type) {
            "etherwarp" -> EtherwarpNode().apply { target = vec(ring.get("localTarget")) }
            "use", "aotv" -> UseItemNode().apply {
                val direction = gson.fromJson(ring.get("rotationVec"), Vec3::class.java).toDirection()
                yaw = direction.yaw
                pitch = direction.pitch
                item = if (type == "aotv") "ASPECT_OF_THE_VOID" else ring.get("itemID").asString
            }
            "boom" -> BoomNode().apply { target = vec(ring.get("target")) }
            "break" -> BreakerNode().apply { blocks = ring.getAsJsonArray("blocks").map(::bp) }
            "bat" -> UseItemNode().apply {
                yaw = ring.get("yaw").asFloat
                pitch = ring.get("pitch").asFloat
                item = "BAT_WAND"
                batAttack = true
            }
            else -> throw JsonParseException("Unsupported RSA node type '$type'")
        }

        node.relative = vec(ring.get("localPos"))
        node.radius = ring.get("radius").asFloat.takeIf { it != 0.5f }
        node.start = ring.get("start").asBoolean.takeIf { it }
        node.unsneak = (type == "aotv" || ring.get("sneak")?.asBoolean == false).takeIf { it }
        node.awaits = readAwaits(ring.getAsJsonObject("awaits"), type)
        return node
    }

    private fun readAwaits(awaits: JsonObject?, type: String): MutableList<RouteAwait>? {
        val conditions = mutableListOf<RouteAwait>()
        awaits?.entrySet()?.forEach { (name, value) ->
            when (name) {
                "awaitSecrets" -> conditions.add(SecretAwait(value.asInt))
                "awaitEWRaytrace" -> if (value.asBoolean) {
                    if (type != "etherwarp") throw JsonParseException("Etherwarp raytrace requires an etherwarp node")
                    conditions.add(RaycastAwait())
                }
                else -> throw JsonParseException("Unsupported RSA await '$name'")
            }
        }
        if (type == "bat") conditions.add(SecretAwait(1))
        return conditions.takeIf { it.isNotEmpty() }
    }

    private fun vec(a: JsonElement): Vec3 {
        return gson.fromJson(a, Vec3::class.java).addVec(x = 15, z = 15)
    }

    private fun bp(a: JsonElement): BlockPos {
        return gson.fromJson(a, BlockPos::class.java).mutable().move(15, 0, 15).immutable()
    }
}
