package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Did
import studio.hypertext.atproto.syntax.Handle
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.io.encoding.Base64

/**
 * Checks records against their lexicon schemas.
 *
 * Every problem is reported with the path of the value it concerns, such as `blocks[0].text`.
 * Data the schema doesn't describe is accepted, so records written by a newer version of a schema
 * stay valid: unknown fields, and members of open unions this schema doesn't list.
 */
public class LexiconRecordValidator(
    private val registry: LexiconRegistry,
) {
    /** Checks [record], a record in the [collection], against that collection's schema. */
    public fun validateRecord(
        collection: Nsid,
        record: JsonObject,
    ): LexiconValidationResult {
        val issues = mutableListOf<LexiconValidationIssue>()
        val definition = registry.resolve(LexiconReference(collection, MAIN_DEFINITION_NAME))
        val schema = definition?.record
        if (definition?.type != LexiconType.RECORD || schema == null) {
            issues += LexiconValidationIssue("", "$collection isn't a known record type")
            return LexiconValidationResult(issues)
        }
        val type = record[TYPE_KEY]
        if (type != null && type.stringOrNull() != collection.toString()) {
            issues += LexiconValidationIssue(TYPE_KEY, "${type.stringOrNull() ?: type.display()} doesn't match the collection $collection")
        }
        Check(issues).value("", record, schema)
        return LexiconValidationResult(issues)
    }

    private inner class Check(
        private val issues: MutableList<LexiconValidationIssue>,
    ) {
        fun value(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            when (field.type) {
                LexiconType.OBJECT -> objectValue(path, value, field)
                LexiconType.STRING -> stringValue(path, value, field)
                LexiconType.INTEGER -> integerValue(path, value, field)
                LexiconType.BOOLEAN -> booleanValue(path, value, field)
                LexiconType.ARRAY -> arrayValue(path, value, field)
                LexiconType.BLOB -> blobValue(path, value, field)
                LexiconType.BYTES -> bytesValue(path, value, field)
                LexiconType.CID_LINK -> if (value.singleString(LINK_KEY) == null) report(path, "not a valid content link")
                LexiconType.UNKNOWN -> if (value !is JsonObject) report(path, "expected an object")
                LexiconType.REF -> refValue(path, value, requireNotNull(field.reference))
                LexiconType.UNION -> unionValue(path, value, field)
                else -> Unit
            }
        }

        private fun objectValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            if (value !is JsonObject) return report(path, "expected an object")
            field.properties.forEach { (name, property) ->
                val propertyPath = if (path.isEmpty()) name else "$path.$name"
                when (val propertyValue = value[name]) {
                    null -> if (property.required) report(propertyPath, "required field is missing")
                    JsonNull -> if (!property.nullable) report(propertyPath, "null isn't allowed")
                    else -> value(propertyPath, propertyValue, property)
                }
            }
        }

        private fun stringValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            val text = value.stringOrNull() ?: return report(path, "expected a string")
            val bytes = text.encodeToByteArray().size.toLong()
            field.maxLength?.let { if (bytes > it) report(path, "longer than $it bytes") }
            field.minLength?.let { if (bytes < it) report(path, "shorter than $it bytes") }
            if (field.maxGraphemes != null || field.minGraphemes != null) {
                val graphemes = graphemeCount(text).toLong()
                field.maxGraphemes?.let { if (graphemes > it) report(path, "longer than $it graphemes") }
                field.minGraphemes?.let { if (graphemes < it) report(path, "shorter than $it graphemes") }
            }
            field.format?.let { format -> if (!matchesFormat(format, text)) report(path, "not a valid $format") }
            closedValues(path, value, field)
        }

        private fun integerValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            val number = (value as? JsonPrimitive)?.takeIf { !it.isString && it.content.all { c -> c.isDigit() || c == '-' } }?.longOrNull
            if (number == null) return report(path, "expected an integer")
            field.minimum?.let { if (number < it) report(path, "less than the minimum of $it") }
            field.maximum?.let { if (number > it) report(path, "more than the maximum of $it") }
            closedValues(path, value, field)
        }

        private fun booleanValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            if ((value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == null) return report(path, "expected a boolean")
            closedValues(path, value, field)
        }

        private fun closedValues(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            val content = (value as JsonPrimitive).content
            field.closedValues?.let { allowed ->
                if (content !in allowed) report(path, "${value.display()} isn't one of the allowed values")
            }
            field.constValue?.let { constant ->
                if (content != constant.content) report(path, "must be ${constant.display()}")
            }
        }

        private fun arrayValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            if (value !is JsonArray) return report(path, "expected an array")
            field.maxLength?.let { if (value.size > it) report(path, "more than $it items") }
            field.minLength?.let { if (value.size < it) report(path, "fewer than $it items") }
            val items = field.items ?: return
            value.forEachIndexed { index, item ->
                if (item == JsonNull) report("$path[$index]", "null isn't allowed") else value("$path[$index]", item, items)
            }
        }

        private fun blobValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            val blob = value as? JsonObject
            val mimeType = blob?.get("mimeType")?.stringOrNull()
            val size = (blob?.get("size") as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            val valid =
                blob?.get(TYPE_KEY)?.stringOrNull() == BLOB_TYPE &&
                    blob["ref"]?.singleString(LINK_KEY) != null &&
                    !mimeType.isNullOrEmpty() &&
                    size != null &&
                    size >= 0
            if (!valid) return report(path, "not a valid blob")
            if (field.accept.isNotEmpty() && field.accept.none { mimeMatches(it, mimeType!!) }) {
                report(path, "$mimeType isn't an accepted type")
            }
            field.maxSize?.let { if (size!! > it) report(path, "$size bytes is more than the maximum of $it") }
        }

        private fun bytesValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            val decoded =
                value.singleString(BYTES_KEY)?.let { encoded -> runCatching { bytesBase64.decode(encoded) }.getOrNull() }
                    ?: return report(path, "not valid bytes")
            field.maxLength?.let { if (decoded.size > it) report(path, "longer than $it bytes") }
            field.minLength?.let { if (decoded.size < it) report(path, "shorter than $it bytes") }
        }

        private fun refValue(
            path: String,
            value: JsonElement,
            reference: LexiconReference,
        ) {
            val target = registry.resolve(reference) ?: return report(path, "the schema refers to ${reference.label()}, which isn't known")
            value(path, value, target.asField())
        }

        private fun unionValue(
            path: String,
            value: JsonElement,
            field: LexiconField,
        ) {
            if (value !is JsonObject) return report(path, "expected an object")
            val type = value[TYPE_KEY]?.stringOrNull() ?: return report(path, "a union member must have a $TYPE_KEY")
            val member = field.refs.firstOrNull { it.unionTypeId() == type }
            when {
                member != null -> refValue(path, value, member)
                field.closed -> report(path, "$TYPE_KEY $type isn't allowed in this closed union")
            }
        }

        private fun report(
            path: String,
            message: String,
        ) {
            issues += LexiconValidationIssue(path, message)
        }
    }

    private companion object {
        const val TYPE_KEY = "\$type"
        const val LINK_KEY = "\$link"
        const val BYTES_KEY = "\$bytes"
        const val BLOB_TYPE = "blob"
        val bytesBase64 = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}

private fun LexiconDefinition.asField(): LexiconField =
    when (type) {
        LexiconType.RECORD -> requireNotNull(record)
        else ->
            LexiconField(
                type = type,
                properties = properties,
                items = items,
                reference = reference,
                knownValues = knownValues,
                refs = refs,
                closed = closed,
                accept = accept,
                maxSize = maxSize,
                minLength = minLength,
                maxLength = maxLength,
                format = format,
                minGraphemes = minGraphemes,
                maxGraphemes = maxGraphemes,
                minimum = minimum,
                maximum = maximum,
                closedValues = closedValues,
                constValue = constValue,
            )
    }

private fun LexiconReference.label(): String = "$documentId#$definitionName"

private fun LexiconReference.unionTypeId(): String =
    if (definitionName == MAIN_DEFINITION_NAME) documentId.toString() else "$documentId#$definitionName"

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonElement.singleString(key: String): String? = (this as? JsonObject)?.takeIf { it.size == 1 }?.get(key)?.stringOrNull()

private fun JsonElement.display(): String = if (this is JsonPrimitive && isString) "\"$content\"" else toString()

private fun mimeMatches(
    pattern: String,
    mimeType: String,
): Boolean = pattern == "*/*" || pattern == mimeType || (pattern.endsWith("/*") && mimeType.startsWith(pattern.dropLast(1)))

private val datetimePattern =
    Regex(
        "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):[0-5]\\d:([0-5]\\d|60)(\\.\\d+)?(Z|[+-]([01]\\d|2[0-3]):[0-5]\\d)$",
    )
private val uriPattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:\\S+$")
private val languagePattern = Regex("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$")
private val cidPattern = Regex("^[A-Za-z0-9+=]{8,256}$")

private fun matchesFormat(
    format: String,
    value: String,
): Boolean =
    when (format) {
        // RFC 3339 with a time zone, as the AT Protocol requires; "-00:00" means "unknown" and isn't allowed.
        "datetime" -> datetimePattern.matches(value) && !value.endsWith("-00:00")
        "uri" -> uriPattern.matches(value)
        "at-uri" -> AtUri.isValid(value)
        "did" -> Did.isValid(value)
        "handle" -> Handle.isValid(value)
        "at-identifier" -> Did.isValid(value) || Handle.isValid(value)
        "nsid" -> Nsid.isValid(value)
        "cid" -> cidPattern.matches(value)
        "language" -> languagePattern.matches(value)
        "tid" -> Tid.isValid(value)
        "record-key" -> RecordKey.isValid(value)
        // A format this version doesn't know, from a newer schema, isn't held against the record.
        else -> true
    }

/**
 * Counts user-perceived characters, close to Unicode extended grapheme clusters: combining marks,
 * zero-width-joined emoji sequences, emoji modifiers and variation selectors, tag sequences,
 * regional-indicator flag pairs, and CRLF each stay part of the character before them.
 */
internal fun graphemeCount(text: String): Int {
    var count = 0
    var index = 0
    var previous = -1
    var regionalRun = 0
    while (index < text.length) {
        val high = text[index]
        val codePoint =
            if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                ((high.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00) + 0x10000
            } else {
                high.code
            }
        index += if (codePoint > 0xFFFF) 2 else 1

        val regional = codePoint in 0x1F1E6..0x1F1FF
        regionalRun = if (regional) regionalRun + 1 else 0
        val joinsPrevious =
            previous != -1 &&
                (
                    previous == ZERO_WIDTH_JOINER ||
                        codePoint == ZERO_WIDTH_JOINER ||
                        extendsCharacter(codePoint) ||
                        (previous == '\r'.code && codePoint == '\n'.code) ||
                        (regional && regionalRun % 2 == 0)
                )
        if (!joinsPrevious) count++
        previous = codePoint
    }
    return count
}

private const val ZERO_WIDTH_JOINER = 0x200D

private fun extendsCharacter(codePoint: Int): Boolean =
    when {
        codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF -> true
        codePoint in 0x1F3FB..0x1F3FF -> true
        codePoint in 0xE0020..0xE007F -> true
        codePoint <= 0xFFFF ->
            codePoint.toChar().category in
                setOf(CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.COMBINING_SPACING_MARK)
        else -> false
    }
