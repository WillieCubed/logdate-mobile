package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
            knownValues = source.strings("knownValues"),
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
            keyRule = source["key"]?.jsonPrimitive?.contentOrNull,
            record = source["record"]?.jsonObject?.let { parseField(owner = owner, source = it) },
            refs = source.references(owner),
            closed = source.boolean("closed"),
            accept = source.strings("accept"),
            maxSize = source.long("maxSize"),
            minLength = source.long("minLength"),
            maxLength = source.long("maxLength"),
            unsupportedType = source.unsupportedTypeName(),
            format = source["format"]?.jsonPrimitive?.contentOrNull,
            minGraphemes = source.long("minGraphemes"),
            maxGraphemes = source.long("maxGraphemes"),
            minimum = source.long("minimum"),
            maximum = source.long("maximum"),
            closedValues = source["enum"]?.let { source.strings("enum") },
            constValue = source["const"] as? JsonPrimitive,
            permissionSet = source.takeIf { type == LexiconType.PERMISSION_SET }?.let(::parsePermissionSet),
            space = source.takeIf { type == LexiconType.SPACE }?.let(::parseSpace),
        )
    }

    private fun parsePermissionSet(source: JsonObject): LexiconPermissionSet =
        LexiconPermissionSet(
            title = source.string("title"),
            titleLang = source.languageMap("title:lang"),
            detail = source.string("detail"),
            detailLang = source.languageMap("detail:lang"),
            permissions =
                (source["permissions"] as? JsonArray)?.map { entry ->
                    val permission = entry as? JsonObject ?: JsonObject(emptyMap())
                    LexiconPermission(
                        type = permission.string("type"),
                        resource = permission.string("resource"),
                        parameters = permission.filterKeys { it != "type" && it != "resource" },
                    )
                },
        )

    private fun parseSpace(source: JsonObject): LexiconSpace =
        LexiconSpace(
            name = source.string("name"),
            nameLang = source.languageMap("name:lang"),
            keyRule = source.string("key"),
            collections = (source["collections"] as? JsonArray)?.let { source.strings("collections") },
        )

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // Keeps every key, so the checker can report a key that isn't a language tag.
    private fun JsonObject.languageMap(key: String): Map<String, String> =
        (this[key] as? JsonObject)
            ?.mapNotNull { (language, text) -> (text as? JsonPrimitive)?.contentOrNull?.let { language to it } }
            ?.toMap()
            .orEmpty()

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
            knownValues = source.strings("knownValues"),
            refs = source.references(owner),
            closed = source.boolean("closed"),
            accept = source.strings("accept"),
            maxSize = source.long("maxSize"),
            minLength = source.long("minLength"),
            maxLength = source.long("maxLength"),
            unsupportedType = source.unsupportedTypeName(),
            format = source["format"]?.jsonPrimitive?.contentOrNull,
            minGraphemes = source.long("minGraphemes"),
            maxGraphemes = source.long("maxGraphemes"),
            minimum = source.long("minimum"),
            maximum = source.long("maximum"),
            closedValues = source["enum"]?.let { source.strings("enum") },
            constValue = source["const"] as? JsonPrimitive,
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

    private fun JsonObject.lexiconType(): LexiconType = typeName()?.let(typesByName::get) ?: LexiconType.UNSUPPORTED

    private fun JsonObject.typeName(): String? = this["type"]?.jsonPrimitive?.contentOrNull?.trim()

    private fun JsonObject.unsupportedTypeName(): String? = typeName().takeIf { lexiconType() == LexiconType.UNSUPPORTED }

    private fun JsonObject.references(owner: Nsid): List<LexiconReference> = strings("refs").map { LexiconReference.parse(owner, it) }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private val typesByName: Map<String, LexiconType> =
        mapOf(
            "object" to LexiconType.OBJECT,
            "query" to LexiconType.QUERY,
            "procedure" to LexiconType.PROCEDURE,
            "params" to LexiconType.PARAMS,
            "string" to LexiconType.STRING,
            "boolean" to LexiconType.BOOLEAN,
            "integer" to LexiconType.INTEGER,
            "array" to LexiconType.ARRAY,
            "blob" to LexiconType.BLOB,
            "bytes" to LexiconType.BYTES,
            "cid-link" to LexiconType.CID_LINK,
            "token" to LexiconType.TOKEN,
            "ref" to LexiconType.REF,
            "union" to LexiconType.UNION,
            "unknown" to LexiconType.UNKNOWN,
            "record" to LexiconType.RECORD,
            "subscription" to LexiconType.SUBSCRIPTION,
            "permission-set" to LexiconType.PERMISSION_SET,
            "permission" to LexiconType.PERMISSION,
            "space" to LexiconType.SPACE,
        )

    private fun JsonObject.stringSet(key: String): Set<String> =
        this[key]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
}
