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
        val required = source.stringSet("required")
        val nullable = source.stringSet("nullable")
        val properties = parseProperties(owner = owner, source = source, required = required, nullable = nullable)
        return LexiconDefinition(
            name = name,
            type = type,
            description = source["description"]?.jsonPrimitive?.contentOrNull,
            required = required,
            nullable = nullable,
            properties = properties,
            items = source["items"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            reference = source["ref"]?.jsonPrimitive?.contentOrNull?.let { LexiconReference.parse(owner, it) },
            knownValues = source.enumValues(),
            parameters = source["parameters"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            input = source["input"]?.jsonObject?.let { parseBody(owner = owner, source = it) },
            output = source["output"]?.jsonObject?.let { parseBody(owner = owner, source = it) },
            errors =
                source["errors"]
                    ?.jsonArray
                    ?.map { error ->
                        val errorObject = error.jsonObject
                        LexiconError(
                            name = requireNotNull(errorObject["name"]?.jsonPrimitive?.contentOrNull) { "lexicon error name is required" },
                            description = errorObject["description"]?.jsonPrimitive?.contentOrNull,
                        )
                    }.orEmpty(),
        )
    }

    private fun parseField(
        owner: Nsid,
        source: JsonObject,
        required: Boolean = false,
        nullable: Boolean = false,
    ): LexiconField =
        LexiconField(
            type = source.lexiconType(),
            description = source["description"]?.jsonPrimitive?.contentOrNull,
            required = required,
            nullable = nullable,
            properties =
                parseProperties(
                    owner = owner,
                    source = source,
                    required = source.stringSet("required"),
                    nullable = source.stringSet("nullable"),
                ),
            items = source["items"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            reference = source["ref"]?.jsonPrimitive?.contentOrNull?.let { LexiconReference.parse(owner, it) },
            knownValues = source.enumValues(),
        )

    private fun parseProperties(
        owner: Nsid,
        source: JsonObject,
        required: Set<String>,
        nullable: Set<String>,
    ): Map<String, LexiconField> =
        source["properties"]
            ?.jsonObject
            ?.entries
            ?.associate { (propertyName, propertySource) ->
                propertyName to
                    parseField(
                        owner = owner,
                        source = propertySource.jsonObject,
                        required = propertyName in required,
                        nullable = propertyName in nullable,
                    )
            }.orEmpty()

    private fun parseBody(
        owner: Nsid,
        source: JsonObject,
    ): LexiconBody =
        LexiconBody(
            encoding = requireNotNull(source["encoding"]?.jsonPrimitive?.contentOrNull) { "lexicon body encoding is required" },
            schema = source["schema"]?.jsonObject?.let { parseField(owner = owner, source = it) },
        )

    private fun JsonObject.lexiconType(): LexiconType =
        when (this["type"]?.jsonPrimitive?.contentOrNull?.trim()) {
            "object" -> LexiconType.OBJECT
            "query" -> LexiconType.QUERY
            "procedure" -> LexiconType.PROCEDURE
            "params" -> LexiconType.PARAMS
            "string" -> LexiconType.STRING
            "boolean" -> LexiconType.BOOLEAN
            "integer" -> LexiconType.INTEGER
            "array" -> LexiconType.ARRAY
            "token" -> LexiconType.TOKEN
            "ref" -> LexiconType.REF
            else -> LexiconType.UNKNOWN
        }

    private fun JsonObject.enumValues(): List<String> =
        (
            (this["enum"] as? JsonArray)?.mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull
            } ?: emptyList()
        ) +
            (
                (this["knownValues"] as? JsonArray)?.mapNotNull { element ->
                    (element as? JsonPrimitive)?.contentOrNull
                } ?: emptyList()
            )

    private fun JsonObject.stringSet(key: String): Set<String> =
        this[key]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
}
