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
)

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
 * Supported lexicon value types used by the current runtime and codegen.
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
    TOKEN,
    REF,
    UNKNOWN,
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
