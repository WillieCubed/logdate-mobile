package app.logdate.client.domain.export.archive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun obj(
    required: List<String>,
    vararg properties: Pair<String, JsonElement>,
): JsonObject =
    buildJsonObject {
        put("type", "object")
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
        put("properties", JsonObject(properties.toMap()))
    }

internal fun array(items: JsonElement): JsonObject =
    buildJsonObject {
        put("type", "array")
        put("items", items)
    }

internal fun string(minLength: Int? = null): JsonObject =
    buildJsonObject {
        put("type", "string")
        if (minLength != null) put("minLength", minLength)
    }

internal fun bool(): JsonObject = buildJsonObject { put("type", "boolean") }

internal fun count(): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("minimum", 0)
    }

internal fun number(
    minimum: Double?,
    maximum: Double?,
): JsonObject =
    buildJsonObject {
        put("type", "number")
        if (minimum != null) put("minimum", minimum)
        if (maximum != null) put("maximum", maximum)
    }

internal fun ref(name: String): JsonObject = buildJsonObject { put("\$ref", "#/\$defs/$name") }

internal fun enumOf(serializer: KSerializer<*>): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(serializer.descriptor.elementNames.map { JsonPrimitive(it) }))
    }
