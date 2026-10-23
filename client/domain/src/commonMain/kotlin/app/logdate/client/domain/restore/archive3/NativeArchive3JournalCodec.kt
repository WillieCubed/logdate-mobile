package app.logdate.client.domain.restore.archive3

import app.logdate.client.domain.restore.ArchiveJsonKeyValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal data class NativeArchive3JournalRecord(
    val id: String,
    val title: String,
    val description: String?,
    val favorite: Boolean?,
    val createdAt: String?,
    val updatedAt: String?,
    val nativeCreatedAtMilliseconds: Long? = null,
)

/** Exact source values remain separate from Android's projections. This grants no import authority. */
internal object NativeArchive3JournalCodec {
    private val required = setOf("id", "title", "description", "favorite", "createdAt", "updatedAt")

    fun decode(text: String): List<NativeArchive3JournalRecord> {
        ArchiveJsonKeyValidation.requireUnique(text)
        val root = Json.parseToJsonElement(text).objectWithKeys(setOf("journals"))
        val journals =
            requireNotNull(root["journals"] as? JsonArray).map { raw ->
                val record = raw.objectWithKeys(required + "nativeCreatedAtMilliseconds", required)
                NativeArchive3JournalRecord(
                    id = record.getValue("id").string().also(::requireCanonicalId),
                    title = record.getValue("title").string(),
                    description = record.getValue("description").nullableString(),
                    favorite = record.getValue("favorite").nullableBoolean(),
                    createdAt = record.getValue("createdAt").nullableString()?.also(::requireSourceInstant),
                    updatedAt = record.getValue("updatedAt").nullableString()?.also(::requireSourceInstant),
                    nativeCreatedAtMilliseconds =
                        record["nativeCreatedAtMilliseconds"]?.let { value ->
                            requireNotNull((value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
                        },
                )
            }
        require(journals.map { it.id }.distinct().size == journals.size) { "Repeated native journal identity" }
        return journals
    }

    fun encode(journals: List<NativeArchive3JournalRecord>): String {
        val text =
            buildJsonObject {
                put(
                    "journals",
                    JsonArray(
                        journals.map { record ->
                            buildJsonObject {
                                put("id", record.id)
                                put("title", record.title)
                                put("description", record.description?.let(::JsonPrimitive) ?: JsonNull)
                                put("favorite", record.favorite?.let(::JsonPrimitive) ?: JsonNull)
                                put("createdAt", record.createdAt?.let(::JsonPrimitive) ?: JsonNull)
                                put("updatedAt", record.updatedAt?.let(::JsonPrimitive) ?: JsonNull)
                                record.nativeCreatedAtMilliseconds?.let { put("nativeCreatedAtMilliseconds", it) }
                            }
                        },
                    ),
                )
            }.toString()
        decode(text)
        return text
    }
}

internal fun JsonElement.objectWithKeys(
    allowed: Set<String>,
    required: Set<String> = allowed,
): JsonObject {
    val value = this as? JsonObject ?: throw IllegalArgumentException("Expected native archive object")
    require(value.keys.all { it in allowed } && value.keys.containsAll(required)) { "Unsupported native archive fields" }
    return value
}

internal fun JsonElement.string(): String = requireNotNull((this as? JsonPrimitive)?.takeIf { it.isString }?.content)

internal fun JsonElement.nullableString(): String? = if (this == JsonNull) null else string()

internal fun requireCanonicalId(raw: String) {
    require(Uuid.parse(raw).toString() == raw) { "Noncanonical native archive identity" }
}

/** RFC3339 Gregorian validation without normalizing away offsets or nanoseconds. */
internal fun requireSourceInstant(raw: String) {
    val match =
        requireNotNull(
            Regex(
                "^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,9}))?(Z|[+-][0-9]{2}:[0-9]{2})$",
            ).matchEntire(raw),
        )
    val (year, month, day, hour, minute) = (1..5).map { match.groupValues[it].toInt() }
    val second = match.groupValues[6].toInt()
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val lengths = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    require(month in 1..12 && day in 1..lengths[month - 1] && hour < 24 && minute < 60 && second < 60)
    val zone = match.groupValues[8]
    require(zone != "-00:00")
    if (zone != "Z") require(zone.substring(1, 3).toInt() < 24 && zone.takeLast(2).toInt() < 60)
}

internal fun sourceInstantParts(raw: String): Pair<Long, Int> {
    requireSourceInstant(raw)
    val year = raw.substring(0, 4).toInt()
    val month = raw.substring(5, 7).toInt()
    val day = raw.substring(8, 10).toInt()
    val adjustedYear = year - if (month <= 2) 1 else 0
    val era = (if (adjustedYear >= 0) adjustedYear else adjustedYear - 399) / 400
    val yearInEra = adjustedYear - era * 400
    val adjustedMonth = month + if (month > 2) -3 else 9
    val dayInYear = (153 * adjustedMonth + 2) / 5 + day - 1
    val days = era * 146097L + yearInEra * 365 + yearInEra / 4 - yearInEra / 100 + dayInYear - 719468
    val zone = Regex("(Z|[+-][0-9]{2}:[0-9]{2})$").find(raw)!!.value
    val offset =
        if (zone ==
            "Z"
        ) {
            0
        } else {
            (zone.substring(1, 3).toInt() * 3600 + zone.takeLast(2).toInt() * 60) * if (zone.first() == '-') -1 else 1
        }
    val seconds =
        days * 86400 + raw.substring(11, 13).toInt() * 3600 + raw.substring(14, 16).toInt() * 60 + raw.substring(17, 19).toInt() - offset
    val fraction =
        raw
            .substring(19)
            .removeSuffix(zone)
            .removePrefix(".")
            .padEnd(9, '0')
            .toInt()
    return seconds to fraction
}

private fun JsonElement.nullableBoolean(): Boolean? {
    if (this == JsonNull) return null
    return requireNotNull((this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull)
}
