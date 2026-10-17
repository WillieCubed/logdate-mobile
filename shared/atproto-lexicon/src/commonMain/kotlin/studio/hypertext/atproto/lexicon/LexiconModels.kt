package studio.hypertext.atproto.lexicon

import studio.hypertext.atproto.syntax.Nsid

/**
 * Parsed AT Protocol lexicon document.
 */
public data class LexiconDocument(
    val lexicon: Int,
    val id: Nsid,
    val definitions: Map<String, LexiconDefinition>,
)

/**
 * Parsed lexicon definition entry.
 */
public data class LexiconDefinition(
    val name: String,
    val type: LexiconType,
    val description: String? = null,
    val required: Set<String> = emptySet(),
    val nullable: Set<String> = emptySet(),
    val properties: Map<String, LexiconField> = emptyMap(),
    val items: LexiconField? = null,
    val reference: LexiconReference? = null,
    val knownValues: List<String> = emptyList(),
    val parameters: LexiconField? = null,
    val input: LexiconBody? = null,
    val output: LexiconBody? = null,
    val errors: List<LexiconError> = emptyList(),
    val keyRule: String? = null,
    val record: LexiconField? = null,
    val refs: List<LexiconReference> = emptyList(),
    val closed: Boolean = false,
    val accept: List<String> = emptyList(),
    val maxSize: Long? = null,
    val minLength: Long? = null,
    val maxLength: Long? = null,
    val unsupportedType: String? = null,
) {
    /** The record-key rule of a `record` definition, or null when it's missing or malformed. */
    val recordKey: LexiconRecordKey?
        get() = keyRule?.let(LexiconRecordKey::parse)
}

/**
 * Parsed field inside a lexicon object or array definition.
 */
public data class LexiconField(
    val type: LexiconType,
    val description: String? = null,
    val required: Boolean = false,
    val nullable: Boolean = false,
    val properties: Map<String, LexiconField> = emptyMap(),
    val items: LexiconField? = null,
    val reference: LexiconReference? = null,
    val knownValues: List<String> = emptyList(),
    val refs: List<LexiconReference> = emptyList(),
    val closed: Boolean = false,
    val accept: List<String> = emptyList(),
    val maxSize: Long? = null,
    val minLength: Long? = null,
    val maxLength: Long? = null,
    val unsupportedType: String? = null,
)

/**
 * Parsed query/procedure input or output body.
 */
public data class LexiconBody(
    val encoding: String,
    val schema: LexiconField? = null,
)

/**
 * Parsed error declaration for queries and procedures.
 */
public data class LexiconError(
    val name: String,
    val description: String? = null,
)

/**
 * Lexicon definition and field types.
 */
public enum class LexiconType {
    OBJECT,
    QUERY,
    PROCEDURE,
    PARAMS,
    STRING,
    BOOLEAN,
    INTEGER,
    ARRAY,
    BLOB,
    BYTES,
    CID_LINK,
    TOKEN,
    REF,
    UNION,
    UNKNOWN,
    RECORD,
    SUBSCRIPTION,
    PERMISSION_SET,
    PERMISSION,

    /** A type name the Lexicon specification doesn't define. */
    UNSUPPORTED,
    ;

    /** Whether this is a primary type, which may only be the `main` definition of a document. */
    public val isPrimary: Boolean
        get() = this == RECORD || this == QUERY || this == PROCEDURE || this == SUBSCRIPTION || this == PERMISSION_SET
}

/**
 * The rule a `record` definition sets for the keys of records in its collection.
 */
public sealed interface LexiconRecordKey {
    /** A timestamp identifier, the usual key for records someone creates over time. */
    public data object Tid : LexiconRecordKey

    /** A key that is itself a valid NSID. */
    public data object Nsid : LexiconRecordKey

    /** Any valid record key. */
    public data object Any : LexiconRecordKey

    /** Exactly one fixed key, such as `self` for a single profile record. */
    public data class Literal(
        val value: String,
    ) : LexiconRecordKey

    public companion object {
        /** Parses a schema's `key` value, or returns null when it isn't a known rule. */
        public fun parse(value: String): LexiconRecordKey? =
            when {
                value == "tid" -> Tid
                value == "nsid" -> Nsid
                value == "any" -> Any
                value.startsWith(LITERAL_PREFIX) ->
                    value.removePrefix(LITERAL_PREFIX).takeIf { it.isNotEmpty() }?.let(::Literal)
                else -> null
            }

        private const val LITERAL_PREFIX = "literal:"
    }
}

/**
 * Parsed reference to another lexicon definition.
 */
public data class LexiconReference(
    val documentId: Nsid,
    val definitionName: String,
) {
    public companion object {
        /**
         * Parses [value] relative to [owner].
         */
        public fun parse(
            owner: Nsid,
            value: String,
        ): LexiconReference {
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "Lexicon ref must not be blank" }
            return when {
                trimmed.startsWith('#') ->
                    LexiconReference(
                        documentId = owner,
                        definitionName = trimmed.removePrefix("#").ifBlank { MAIN_DEFINITION_NAME },
                    )

                '#' in trimmed -> {
                    val documentId = Nsid.require(trimmed.substringBefore('#'))
                    val definitionName = trimmed.substringAfter('#').ifBlank { MAIN_DEFINITION_NAME }
                    LexiconReference(documentId = documentId, definitionName = definitionName)
                }

                else ->
                    LexiconReference(
                        documentId = Nsid.require(trimmed),
                        definitionName = MAIN_DEFINITION_NAME,
                    )
            }
        }
    }
}

internal const val MAIN_DEFINITION_NAME: String = "main"
