package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.syntax.Nsid

/**
 * Parses AT Protocol lexicon JSON documents into typed Kotlin models.
 */
public object LexiconParser {
    private val json: Json = Json { ignoreUnknownKeys = true }

    /**
     * Parses an encoded lexicon [source].
     */
    public fun parse(source: String): LexiconDocument = parse(json.parseToJsonElement(source).jsonObject)

    /**
     * Parses a lexicon [source].
     */
    public fun parse(source: JsonObject): LexiconDocument {
        val lexicon = requireNotNull(source["lexicon"]?.jsonPrimitive?.intOrNull) { "lexicon version is required" }
        val id = Nsid.require(requireNotNull(source["id"]?.jsonPrimitive?.contentOrNull) { "id is required" })
        val defs = source["defs"]?.jsonObject ?: emptyMap<String, kotlinx.serialization.json.JsonElement>().let(::JsonObject)
        val definitions =
            defs.entries.associate { (name, value) ->
                name to parseDefinition(owner = id, name = name, source = value.jsonObject)
            }
        return LexiconDocument(
            lexicon = lexicon,
            id = id,
            definitions = definitions,
        )
    }

    private fun parseDefinition(
        owner: Nsid,
        name: String,
        source: JsonObject,
    ): LexiconDefinition {
        val type = source.lexiconType()
        val required =
            source["required"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty()
        val properties =
            source["properties"]
                ?.jsonObject
                ?.entries
                ?.associate { (propertyName, propertySource) ->
                    propertyName to
                        parseField(
                            owner = owner,
                            source = propertySource.jsonObject,
                            required = propertyName in required,
                        )
                }.orEmpty()
        return LexiconDefinition(
            name = name,
            type = type,
            description = source["description"]?.jsonPrimitive?.contentOrNull,
            required = required,
            properties = properties,
            items = source["items"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            reference = source["ref"]?.jsonPrimitive?.contentOrNull?.let { LexiconReference.parse(owner, it) },
            knownValues = source.enumValues(),
        )
    }

    private fun parseField(
        owner: Nsid,
        source: JsonObject,
        required: Boolean = false,
    ): LexiconField =
        LexiconField(
            type = source.lexiconType(),
            description = source["description"]?.jsonPrimitive?.contentOrNull,
            required = required,
            items = source["items"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            reference = source["ref"]?.jsonPrimitive?.contentOrNull?.let { LexiconReference.parse(owner, it) },
            knownValues = source.enumValues(),
        )

    private fun JsonObject.lexiconType(): LexiconType =
        when (this["type"]?.jsonPrimitive?.contentOrNull?.trim()) {
            "object" -> LexiconType.OBJECT
            "string" -> LexiconType.STRING
            "boolean" -> LexiconType.BOOLEAN
            "integer" -> LexiconType.INTEGER
            "array" -> LexiconType.ARRAY
            "token" -> LexiconType.TOKEN
            "ref" -> LexiconType.REF
            else -> LexiconType.UNKNOWN
        }

    private fun JsonObject.enumValues(): List<String> =
        (this["enum"] as? JsonArray)
            ?.mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull
            }.orEmpty()
}
