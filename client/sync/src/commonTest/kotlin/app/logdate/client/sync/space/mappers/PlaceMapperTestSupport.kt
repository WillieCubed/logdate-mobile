package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.ConversionVectorFixtures
import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.canonicalRecordKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Writes [value] as a record, checks the record against its lexicon and reads it back, the way the
 * transport does: under the lowercase form of the record key.
 */
internal fun <T : Any> SpaceRecordMapper<T>.roundTrip(value: T): T {
    val record = toRecord(value)
    assertValidRecord(collection, record)
    return fromRecord(canonicalRecordKey(rkeyOf(value)), record)
}

/** [this] with [key] set to the string [value]. */
internal fun JsonObject.with(
    key: String,
    value: String,
) = JsonObject(this + (key to JsonPrimitive(value)))

/** One known answer from `tools/lexicon-check/conversion-vectors.json`: the integer to write, or null when the field is refused. */
internal class ConversionVector(
    val kind: String,
    val input: Double,
    val expected: Long?,
) {
    override fun toString() = "$kind($input) = $expected"
}

/** The shared known answers of one [kind]: `centimeters`, `bearing` or `thousandths`. */
internal fun conversionVectors(kind: String): List<ConversionVector> =
    Json
        .parseToJsonElement(ConversionVectorFixtures.text("conversion-vectors.json"))
        .jsonObject
        .getValue("fields")
        .jsonArray
        .map { it.jsonObject }
        .filter { it.getValue("kind").jsonPrimitive.content == kind }
        .map { case ->
            val input = case.getValue("input").jsonPrimitive
            ConversionVector(
                kind,
                if (!input.isString) {
                    input.double
                } else {
                    when (input.content) {
                        "NaN" -> Double.NaN
                        "Infinity" -> Double.POSITIVE_INFINITY
                        "-Infinity" -> Double.NEGATIVE_INFINITY
                        else -> error("Unknown vector input ${input.content}")
                    }
                },
                case.getValue("expected").let { if (it is JsonNull) null else it.jsonPrimitive.long },
            )
        }

/** The vectors a 32-bit float carries exactly, which is what a device's accuracy, speed and bearing are. */
internal fun ConversionVector.floatExact(): Boolean = input.isNaN() || input.isInfinite() || input.toFloat().toDouble() == input
