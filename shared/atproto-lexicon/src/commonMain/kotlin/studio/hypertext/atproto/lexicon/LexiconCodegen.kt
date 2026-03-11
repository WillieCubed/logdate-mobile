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
        val documentClassName = document.id.documentPrefix()
        val generatedClasses =
            linkedMapOf<String, GeneratedClass>()
                .apply {
                    document.definitions.values
                        .sortedBy { if (it.name == MAIN_DEFINITION_NAME) "" else it.name }
                        .forEach { definition ->
                            when (definition.type) {
                                LexiconType.OBJECT, LexiconType.PARAMS ->
                                    put(
                                        className(document.id, definition.name),
                                        GeneratedClass(
                                            name = className(document.id, definition.name),
                                            properties = definition.properties,
                                        ),
                                    )

                                LexiconType.QUERY, LexiconType.PROCEDURE -> {
                                    definition.parameters?.let { parameters ->
                                        put(
                                            "${documentClassName}Params",
                                            GeneratedClass(
                                                name = "${documentClassName}Params",
                                                properties = parameters.properties,
                                            ),
                                        )
                                    }
                                    definition.input
                                        ?.schema
                                        ?.takeIf { it.type.isObjectLike() }
                                        ?.let { input ->
                                            put(
                                                "${documentClassName}Input",
                                                GeneratedClass(
                                                    name = "${documentClassName}Input",
                                                    properties = input.properties,
                                                ),
                                            )
                                        }
                                    definition.output
                                        ?.schema
                                        ?.takeIf { it.type.isObjectLike() }
                                        ?.let { output ->
                                            put(
                                                "${documentClassName}Output",
                                                GeneratedClass(
                                                    name = "${documentClassName}Output",
                                                    properties = output.properties,
                                                ),
                                            )
                                        }
                                }

                                else -> Unit
                            }
                        }
                }.values
                .toList()

        val needsJsonElement =
            generatedClasses.any { generatedClass ->
                generatedClass.properties.values.any { field -> kotlinType(field).contains("JsonElement") }
            }

        return buildString {
            appendLine("package $packageName")
            appendLine()
            if (generatedClasses.isNotEmpty()) {
                appendLine("import kotlinx.serialization.Serializable")
            }
            if (needsJsonElement) {
                appendLine("import kotlinx.serialization.json.JsonElement")
            }
            if (generatedClasses.isNotEmpty() || needsJsonElement) {
                appendLine()
            }
            appendLine("public object ${documentClassName}Lexicon {")
            appendLine("    public const val ID: String = \"${document.id}\"")
            appendLine("}")
            generatedClasses.forEach { generatedClass ->
                appendLine()
                appendLine("@Serializable")
                appendLine("public data class ${generatedClass.name}(")
                val properties =
                    generatedClass.properties.entries
                        .sortedBy { it.key }
                        .map { (name, field) ->
                            "    val ${name.asIdentifier()}: ${kotlinType(field)}"
                        }
                if (properties.isEmpty()) {
                    appendLine("    val unused: Boolean = true,")
                } else {
                    properties.forEach { appendLine("$it,") }
                }
                appendLine(")")
            }
        }
    }

    private fun className(
        documentId: studio.hypertext.atproto.syntax.Nsid,
        definitionName: String,
    ): String {
        val documentPrefix = documentId.documentPrefix()
        return when (definitionName) {
            MAIN_DEFINITION_NAME -> documentPrefix
            else -> documentPrefix + definitionName.asPascalCase()
        }
    }

    private fun kotlinType(field: LexiconField): String {
        val baseType = baseKotlinType(field)
        return if (field.required && !field.nullable) {
            baseType
        } else {
            "$baseType?"
        }
    }

    private fun baseKotlinType(field: LexiconField): String =
        when (field.type) {
            LexiconType.STRING, LexiconType.TOKEN -> "String"
            LexiconType.BOOLEAN -> "Boolean"
            LexiconType.INTEGER -> "Long"
            LexiconType.ARRAY -> "List<${arrayElementType(requireNotNull(field.items))}>"
            LexiconType.BLOB -> "JsonElement"
            LexiconType.REF -> className(requireNotNull(field.reference).documentId, requireNotNull(field.reference).definitionName)
            LexiconType.OBJECT, LexiconType.PARAMS, LexiconType.QUERY, LexiconType.PROCEDURE, LexiconType.UNKNOWN -> "JsonElement"
        }

    private fun arrayElementType(field: LexiconField): String {
        val baseType = baseKotlinType(field)
        return if (field.nullable) {
            "$baseType?"
        } else {
            baseType
        }
    }

    private fun studio.hypertext.atproto.syntax.Nsid.documentPrefix(): String =
        toString()
            .substringAfterLast('.')
            .asPascalCase()

    private fun LexiconType.isObjectLike(): Boolean = this == LexiconType.OBJECT || this == LexiconType.PARAMS

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

    private data class GeneratedClass(
        val name: String,
        val properties: Map<String, LexiconField>,
    )
}
