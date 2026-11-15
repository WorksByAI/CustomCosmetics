package quoi.api.autoroutes.converters

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import quoi.api.autoroutes.RouteNode
import quoi.config.ConfigSystem.gson
import java.io.File

object RouteImport {
    private val nativeType = object : TypeToken<Map<String, List<RouteNode>>>() {}.type

    fun read(file: File): Map<String, List<RouteNode>> {
        val json = file.reader(Charsets.UTF_8).use(JsonParser::parseReader)
        if (!json.isJsonObject) throw JsonParseException("Expected an object keyed by room name")
        val rooms = json.asJsonObject
        val firstNode = rooms.entrySet().asSequence().flatMap { (room, nodes) ->
            if (!nodes.isJsonArray) throw JsonParseException("Expected a route array for $room")
            nodes.asJsonArray.asSequence()
        }.firstOrNull()
        return if (firstNode?.isJsonObject == true && firstNode.asJsonObject.has("localPos")) {
            RsaConverter.convert(rooms)
        } else {
            gson.fromJson(rooms, nativeType)
        }
    }
}
