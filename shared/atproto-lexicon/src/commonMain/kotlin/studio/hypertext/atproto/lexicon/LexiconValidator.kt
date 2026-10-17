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

        val checker = Checker(document, registry, issues)
        document.definitions.values.forEach(checker::checkDefinition)
        return LexiconValidationResult(issues = issues)
    }

    private class Checker(
        private val owner: LexiconDocument,
        private val registry: LexiconRegistry,
        private val issues: MutableList<LexiconValidationIssue>,
    ) {
        fun checkDefinition(definition: LexiconDefinition) {
            val path = "${owner.id}#${definition.name}"
            if (definition.type.isPrimary && definition.name != MAIN_DEFINITION_NAME) {
                report(path, "${definition.type.lexiconName()} definitions must be named main")
            }
            if (definition.type == LexiconType.RECORD) checkRecord(path, definition)
            checkField(path, definition.asField(), requiredNames = definition.required)
            definition.parameters?.let { checkField("$path.parameters", it) }
            definition.input?.schema?.let { checkField("$path.input", it) }
            definition.output?.schema?.let { checkField("$path.output", it) }
        }

        private fun checkRecord(
            path: String,
            definition: LexiconDefinition,
        ) {
            val keyRule = definition.keyRule
            when {
                keyRule == null -> report(path, "Records must declare a key")
                definition.recordKey == null -> report(path, "Unknown record key rule '$keyRule'")
            }
            val record = definition.record
            if (record?.type != LexiconType.OBJECT) {
                report(path, "A record's record must be an object")
                return
            }
            checkField(path, record, requiredNames = record.properties.filterValues { it.required }.keys)
        }

        private fun checkField(
            path: String,
            field: LexiconField,
            requiredNames: Set<String> = emptySet(),
        ) {
            when (field.type) {
                LexiconType.REF -> checkRef(path, field.reference)
                LexiconType.UNION -> checkUnion(path, field)
                LexiconType.BLOB -> checkBlob(path, field)
                LexiconType.BYTES -> checkLengths(path, field)
                LexiconType.UNSUPPORTED -> report(path, "Unsupported lexicon type '${field.unsupportedType}'")
                LexiconType.ARRAY ->
                    field.items?.let { checkField("$path[]", it) }
                        ?: report(path, "array definitions must declare items")
                LexiconType.OBJECT, LexiconType.PARAMS -> {
                    requiredNames.filterNot(field.properties::containsKey).forEach { name ->
                        report(path, "Required field '$name' is missing from properties")
                    }
                    field.properties.forEach { (name, property) -> checkField("$path.$name", property) }
                }
                else -> Unit
            }
        }

        private fun checkRef(
            path: String,
            reference: LexiconReference?,
        ) {
            if (reference == null) {
                report(path, "ref definitions must include a ref target")
                return
            }
            if (resolve(reference) == null) report(path, "Unresolved ref target ${reference.label()}")
        }

        private fun checkUnion(
            path: String,
            field: LexiconField,
        ) {
            if (field.closed && field.refs.isEmpty()) report(path, "A closed union must list at least one ref")
            field.refs.forEach { reference ->
                val target = resolve(reference)
                when {
                    target == null -> report(path, "Unresolved ref target ${reference.label()}")
                    target.type != LexiconType.OBJECT && target.type != LexiconType.RECORD ->
                        report(path, "Union member ${reference.label()} must be an object or a record")
                }
            }
        }

        private fun checkBlob(
            path: String,
            field: LexiconField,
        ) {
            field.accept.filterNot(::isAcceptableMimePattern).forEach { entry ->
                report(path, "Blob accept entry '$entry' must be a MIME type, type/* or */*")
            }
            if ((field.maxSize ?: 0) < 0) report(path, "maxSize must not be negative")
        }

        private fun checkLengths(
            path: String,
            field: LexiconField,
        ) {
            val minLength = field.minLength
            val maxLength = field.maxLength
            if ((minLength ?: 0) < 0) report(path, "minLength must not be negative")
            if ((maxLength ?: 0) < 0) report(path, "maxLength must not be negative")
            if (minLength != null && maxLength != null && minLength > maxLength) {
                report(path, "minLength $minLength is greater than maxLength $maxLength")
            }
        }

        private fun resolve(reference: LexiconReference): LexiconDefinition? =
            when (reference.documentId) {
                owner.id -> owner.definitions[reference.definitionName]
                else -> registry.resolve(reference)
            }

        private fun report(
            path: String,
            message: String,
        ) {
            issues += LexiconValidationIssue(path = path, message = message)
        }
    }

    private fun LexiconDefinition.asField(): LexiconField =
        LexiconField(
            type = type,
            properties = properties,
            items = items,
            reference = reference,
            refs = refs,
            closed = closed,
            accept = accept,
            maxSize = maxSize,
            minLength = minLength,
            maxLength = maxLength,
            unsupportedType = unsupportedType,
        )

    private fun LexiconReference.label(): String = "$documentId#$definitionName"

    private fun LexiconType.lexiconName(): String = name.lowercase().replace('_', '-')

    // A blob's accept list holds MIME types, globs over a whole subtype, or the any-type glob.
    private fun isAcceptableMimePattern(entry: String): Boolean = entry == "*/*" || mimePattern.matches(entry)

    private val mimePattern = Regex("^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/([A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*|\\*)$")
}
