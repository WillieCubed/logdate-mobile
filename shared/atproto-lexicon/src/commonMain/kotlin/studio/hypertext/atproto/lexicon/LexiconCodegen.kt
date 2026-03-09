package studio.hypertext.atproto.lexicon

/**
 * Deterministic Kotlin source generator for supported lexicon documents.
 */
public object LexiconCodegen {
    /**
     * Generates Kotlin source for [document].
     */
    public fun generate(
        document: LexiconDocument,
        packageName: String = "studio.hypertext.atproto.lexicon.generated",
    ): String {
        val documentClassName =
            document.id
                .toString()
                .substringAfterLast('.')
                .asPascalCase()
        val objectDefinitions =
            document.definitions.values
                .filter { it.type == LexiconType.OBJECT }
                .sortedBy { if (it.name == MAIN_DEFINITION_NAME) "" else it.name }

        val needsJsonElement =
            objectDefinitions.any { definition ->
                definition.properties.values.any { field -> kotlinType(document, field).contains("JsonElement") }
            }

        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import kotlinx.serialization.Serializable")
            if (needsJsonElement) {
                appendLine("import kotlinx.serialization.json.JsonElement")
            }
            appendLine()
            appendLine("public object ${documentClassName}Lexicon {")
            appendLine("    public const val id: String = \"${document.id}\"")
            appendLine("}")
            objectDefinitions.forEach { definition ->
                appendLine()
                appendLine("@Serializable")
                appendLine("public data class ${className(document, definition.name)}(")
                val properties =
                    definition.properties.entries
                        .sortedBy { it.key }
                        .map { (name, field) ->
                            "    val ${name.asIdentifier()}: ${kotlinType(document, field)}"
                        }
                if (properties.isEmpty()) {
                    appendLine("    val unused: Boolean = true,")
                } else {
                    properties.forEach { appendLine("$it,") }
                }
                appendLine(")")
            }
        }.trimEnd()
    }

    private fun className(
        document: LexiconDocument,
        definitionName: String,
    ): String {
        val documentPrefix =
            document.id
                .toString()
                .substringAfterLast('.')
                .asPascalCase()
        return when (definitionName) {
            MAIN_DEFINITION_NAME -> documentPrefix
            else -> documentPrefix + definitionName.asPascalCase()
        }
    }

    private fun kotlinType(
        document: LexiconDocument,
        field: LexiconField,
    ): String {
        val baseType =
            when (field.type) {
                LexiconType.STRING, LexiconType.TOKEN -> "String"
                LexiconType.BOOLEAN -> "Boolean"
                LexiconType.INTEGER -> "Long"
                LexiconType.ARRAY -> "List<${kotlinType(document, requireNotNull(field.items))}>"
                LexiconType.REF -> className(document, requireNotNull(field.reference).definitionName)
                LexiconType.OBJECT, LexiconType.UNKNOWN -> "JsonElement"
            }
        return if (field.required) {
            baseType
        } else {
            "$baseType?"
        }
    }

    private fun String.asPascalCase(): String =
        split('.', '-', '_')
            .filter(String::isNotBlank)
            .joinToString(separator = "") { token ->
                token.replaceFirstChar { char -> char.uppercase() }
            }

    private fun String.asIdentifier(): String =
        replaceFirstChar { char ->
            char.lowercase()
        }
}
