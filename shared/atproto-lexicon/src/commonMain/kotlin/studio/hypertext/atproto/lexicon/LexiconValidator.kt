package studio.hypertext.atproto.lexicon

/**
 * Validation result for a parsed lexicon document.
 */
public data class LexiconValidationResult(
    val issues: List<LexiconValidationIssue>,
) {
    /**
     * Whether the document passed validation with no issues.
     */
    public val isValid: Boolean
        get() = issues.isEmpty()
}

/**
 * Single lexicon validation issue.
 */
public data class LexiconValidationIssue(
    val path: String,
    val message: String,
)

/**
 * Validates parsed lexicon documents against the runtime's supported rules.
 */
public object LexiconValidator {
    /**
     * Validates [document] with optional cross-document [registry] lookups.
     */
    public fun validate(
        document: LexiconDocument,
        registry: LexiconRegistry = LexiconRegistry(),
    ): LexiconValidationResult {
        val issues = mutableListOf<LexiconValidationIssue>()
        if (document.lexicon != 1) {
            issues += LexiconValidationIssue(path = document.id.toString(), message = "Only lexicon version 1 is supported")
        }
        if (document.definitions.isEmpty()) {
            issues += LexiconValidationIssue(path = "${document.id}#defs", message = "Lexicon must define at least one definition")
        }

        document.definitions.values.forEach { definition ->
            if (definition.type.isObjectLike()) {
                definition.required.forEach { requiredField ->
                    if (requiredField !in definition.properties) {
                        issues +=
                            LexiconValidationIssue(
                                path = "${document.id}#${definition.name}",
                                message = "Required field '$requiredField' is missing from properties",
                            )
                    }
                }
            }
            validateField(
                owner = document,
                path = "${document.id}#${definition.name}",
                field =
                    LexiconField(
                        type = definition.type,
                        properties = definition.properties,
                        items = definition.items,
                        reference = definition.reference,
                    ),
                registry = registry,
                issues = issues,
            )
            definition.parameters?.let { parameters ->
                validateField(
                    owner = document,
                    path = "${document.id}#${definition.name}.parameters",
                    field = parameters,
                    registry = registry,
                    issues = issues,
                )
            }
            definition.input?.schema?.let { schema ->
                validateField(
                    owner = document,
                    path = "${document.id}#${definition.name}.input",
                    field = schema,
                    registry = registry,
                    issues = issues,
                )
            }
            definition.output?.schema?.let { schema ->
                validateField(
                    owner = document,
                    path = "${document.id}#${definition.name}.output",
                    field = schema,
                    registry = registry,
                    issues = issues,
                )
            }
            definition.properties.forEach { (name, property) ->
                validateField(
                    owner = document,
                    path = "${document.id}#${definition.name}.$name",
                    field = property,
                    registry = registry,
                    issues = issues,
                )
            }
        }

        return LexiconValidationResult(issues = issues)
    }

    private fun validateField(
        owner: LexiconDocument,
        path: String,
        field: LexiconField,
        registry: LexiconRegistry,
        issues: MutableList<LexiconValidationIssue>,
    ) {
        when (field.type) {
            LexiconType.REF -> {
                val reference = field.reference
                if (reference == null) {
                    issues += LexiconValidationIssue(path = path, message = "ref definitions must include a ref target")
                } else {
                    val resolved =
                        when (reference.documentId) {
                            owner.id -> owner.definitions[reference.definitionName]
                            else -> registry.resolve(reference)
                        }
                    if (resolved == null) {
                        issues +=
                            LexiconValidationIssue(
                                path = path,
                                message = "Unresolved ref target ${reference.documentId}#${reference.definitionName}",
                            )
                    }
                }
            }

            LexiconType.ARRAY -> {
                if (field.items == null) {
                    issues += LexiconValidationIssue(path = path, message = "array definitions must declare items")
                } else {
                    validateField(owner = owner, path = "$path[]", field = field.items, registry = registry, issues = issues)
                }
            }

            else -> {
                if (field.type.isObjectLike()) {
                    field.properties.forEach { (name, property) ->
                        validateField(
                            owner = owner,
                            path = "$path.$name",
                            field = property,
                            registry = registry,
                            issues = issues,
                        )
                    }
                }
            }
        }
    }

    private fun LexiconType.isObjectLike(): Boolean = this == LexiconType.OBJECT || this == LexiconType.PARAMS
}
