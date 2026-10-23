package app.logdate.client.domain.restore.archive3

import app.logdate.client.domain.restore.ArchiveJsonKeyValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Evaluates only the pinned local native schemas; archive-supplied schemas never execute. */
internal object NativeArchive3SchemaValidation {
    fun document(
        name: String,
        text: String,
    ): JsonObject {
        ArchiveJsonKeyValidation.requireUnique(text)
        val value = Json.parseToJsonElement(text)
        val schema = Json.parseToJsonElement(NativeArchive3Schemas.documents.getValue(name)).jsonObject
        validate(value, schema, schema)
        return value as? JsonObject ?: throw IllegalArgumentException("Invalid native archive document")
    }

    private fun validate(
        value: JsonElement,
        schema: JsonObject,
        root: JsonObject,
    ) {
        schema["\$ref"]?.string()?.let { reference ->
            require(reference.startsWith("#/"))
            val resolved = reference.removePrefix("#/").split('/').fold(root as JsonElement) { item, key -> item.jsonObject.getValue(key) }
            validate(value, resolved.jsonObject, root)
        }
        schema["const"]?.let { require(value == it) }
        schema["enum"]?.let { require(value in it.jsonArray) }
        validateType(value, schema)
        validateCompositions(value, schema, root)
        if (value is JsonObject) validateObject(value, schema, root)
        if (value is JsonArray) schema["items"]?.let { item -> value.forEach { validate(it, item.jsonObject, root) } }
        validatePrimitive(value, schema)
    }

    private fun validateType(
        value: JsonElement,
        schema: JsonObject,
    ) {
        schema["type"]?.string()?.let { type ->
            require(
                when (type) {
                    "object" -> value is JsonObject
                    "array" -> value is JsonArray
                    "string" -> value is JsonPrimitive && value.isString
                    "null" -> value == JsonNull
                    "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
                    "integer" -> value is JsonPrimitive && !value.isString && value.longOrNull != null
                    "number" -> value is JsonPrimitive && !value.isString && value.doubleOrNull?.isFinite() == true
                    else -> false
                },
            ) { "Invalid native archive value type" }
        }
    }

    private fun validateCompositions(
        value: JsonElement,
        schema: JsonObject,
        root: JsonObject,
    ) {
        schema["allOf"]?.jsonArray?.forEach { validate(value, it.jsonObject, root) }
        schema["anyOf"]?.jsonArray?.let { choices ->
            require(choices.any { runCatching { validate(value, it.jsonObject, root) }.isSuccess }) { "Invalid native archive alternative" }
        }
        schema["if"]?.let { condition ->
            if (runCatching {
                    validate(
                        value,
                        condition.jsonObject,
                        root,
                    )
                }.isSuccess
            ) {
                schema["then"]?.let { validate(value, it.jsonObject, root) }
            }
        }
    }

    private fun validateObject(
        value: JsonObject,
        schema: JsonObject,
        root: JsonObject,
    ) {
        schema["required"]?.jsonArray?.forEach { require(it.string() in value) { "Missing native archive field" } }
        val properties = schema["properties"]?.jsonObject.orEmpty()
        if (schema["additionalProperties"] ==
            JsonPrimitive(false)
        ) {
            require(value.keys.all { it in properties }) { "Unknown native archive field" }
        }
        value.forEach { (key, item) -> properties[key]?.let { validate(item, it.jsonObject, root) } }
        schema["dependentRequired"]?.jsonObject?.forEach { (key, required) ->
            if (key in value) required.jsonArray.forEach { require(it.string() in value) }
        }
    }

    private fun validatePrimitive(
        value: JsonElement,
        schema: JsonObject,
    ) {
        if (value is JsonPrimitive && value.isString) {
            schema["minLength"]?.jsonPrimitive?.longOrNull?.let { require(value.content.length >= it) }
            schema["maxLength"]?.jsonPrimitive?.longOrNull?.let { require(value.content.length <= it) }
            schema["pattern"]?.string()?.let { require(Regex(it).containsMatchIn(value.content)) }
            if (schema["pattern"]?.string()?.startsWith("^[0-9]{4}-") == true &&
                value.content.contains('T')
            ) {
                requireSourceInstant(value.content)
            }
        }
        if (value is JsonPrimitive && !value.isString) {
            value.doubleOrNull?.let { number ->
                schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let { require(number >= it) }
                schema["maximum"]?.jsonPrimitive?.doubleOrNull?.let { require(number <= it) }
            }
        }
    }
}
