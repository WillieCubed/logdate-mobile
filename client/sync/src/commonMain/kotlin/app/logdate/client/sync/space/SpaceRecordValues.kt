package app.logdate.client.sync.space

import app.logdate.lexicon.app.logdate.DefsRecordRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.lexicon.data.LexiconData
import studio.hypertext.atproto.syntax.Datetime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import kotlin.time.Instant
import kotlin.uuid.Uuid

/*
 * The value conventions every kind's records share, so each kind writes numbers, dates and keys
 * the same way and another client reads them back the same way:
 * - Fractions become whole numbers of a fixed unit (thousandths, centimeters, hundredths of a
 *   degree), rounded half away from zero: 2.5 becomes 3 and -2.5 becomes -3.
 * - NaN and infinity are refused rather than written as some number.
 * - A bearing is taken modulo a full turn into [0, 36000) hundredths of a degree, so -90° is
 *   27000 and 360° is 0.
 * - Record keys match regardless of case, and lowercase is the form every client writes.
 *
 * Every function names the record and field it was given as `owner` in its failure, never the
 * value, which could be private.
 */

/** [record] as its JSON record value. */
fun <T> encodeRecord(
    serializer: KSerializer<T>,
    record: T,
): JsonObject = LexiconData.json.encodeToJsonElement(serializer, record).jsonObject

/**
 * Reads [record], stored under [rkey] in [collection]. A record whose `$type` names another type
 * is refused.
 */
fun <T> decodeRecord(
    serializer: KSerializer<T>,
    collection: String,
    rkey: String,
    record: JsonObject,
): T {
    val type = (record["\$type"] as? JsonPrimitive)?.contentOrNull
    if (type != null && type != collection) {
        throw SpaceRecordMappingException("$collection/$rkey: the record is a $type")
    }
    return try {
        LexiconData.json.decodeFromJsonElement(serializer, record)
    } catch (error: SerializationException) {
        throw SpaceRecordMappingException("$collection/$rkey: the record doesn't match its schema", error)
    } catch (error: IllegalArgumentException) {
        throw SpaceRecordMappingException("$collection/$rkey: the record doesn't match its schema", error)
    }
}

/** The form every client writes [rkey] in: lowercase. */
fun canonicalRecordKey(rkey: String): String = rkey.lowercase()

/** Whether two record keys name the same record, whatever case each is written in. */
fun sameRecordKey(
    first: String,
    second: String,
): Boolean = first.equals(second, ignoreCase = true)

/** The LogDate id a record key holds, in either case. */
fun parseUuidKey(
    collection: String,
    rkey: String,
): Uuid =
    Uuid.parseOrNull(canonicalRecordKey(rkey)) ?: throw SpaceRecordMappingException("$collection/$rkey: the record key isn't a LogDate id")

/** A reference to the record of type [collection] keyed by [id], in the canonical lowercase form. */
fun recordRef(
    collection: String,
    id: Uuid,
): DefsRecordRef = DefsRecordRef(collection = collection, rkey = canonicalRecordKey(id.toString()))

/** A datetime string with the instant's full precision. */
fun Instant.toDatetime(owner: String): String {
    val text = toString()
    if (!Datetime.isValid(text)) throw SpaceRecordMappingException("$owner can't be written as a datetime")
    return text
}

fun parseDatetime(
    owner: String,
    value: String,
): Instant =
    Instant.parseOrNull(value)?.takeIf { Datetime.isValid(value) }
        ?: throw SpaceRecordMappingException("$owner isn't a valid datetime")

/** [value] rounded to a whole number, halves away from zero. NaN and infinity are refused. */
fun roundHalfAwayFromZero(
    owner: String,
    value: Double,
): Long {
    if (!value.isFinite()) throw SpaceRecordMappingException("$owner isn't a finite number")
    val rounded = value.sign * floor(abs(value) + 0.5)
    if (rounded >= Long.MAX_VALUE.toDouble() || rounded < Long.MIN_VALUE.toDouble()) {
        throw SpaceRecordMappingException("$owner is too large to store")
    }
    return rounded.toLong()
}

/**
 * The number as a plain decimal string, such as `0.00001` rather than `1.0E-5`, with every digit
 * needed to read back the same [Double].
 */
fun Double.toDecimalString(owner: String): String {
    if (!isFinite()) throw SpaceRecordMappingException("$owner isn't a finite number")
    val text = toString()
    val exponentAt = text.indexOfFirst { it == 'E' || it == 'e' }
    if (exponentAt < 0) return text

    val negative = text.startsWith('-')
    val mantissa = text.substring(if (negative) 1 else 0, exponentAt)
    val exponent = text.substring(exponentAt + 1).toInt()
    val pointAt = mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length
    val digits = mantissa.removeRange(pointAt, minOf(pointAt + 1, mantissa.length))
    val newPointAt = pointAt + exponent
    val plain =
        when {
            newPointAt <= 0 -> "0." + "0".repeat(-newPointAt) + digits
            newPointAt >= digits.length -> digits + "0".repeat(newPointAt - digits.length) + ".0"
            else -> digits.substring(0, newPointAt) + "." + digits.substring(newPointAt)
        }
    val whole = plain.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val fraction = plain.substringAfter('.').trimEnd('0').ifEmpty { "0" }
    return (if (negative) "-" else "") + whole + "." + fraction
}

private val decimalPattern = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$")

fun parseDecimal(
    owner: String,
    value: String,
): Double =
    value
        .takeIf { decimalPattern.matches(it) }
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?: throw SpaceRecordMappingException("$owner isn't a decimal number")

/** A confidence from 0 to 1 as whole thousandths, kept between 0 and 1000. */
fun Float.toThousandths(owner: String): Long = roundHalfAwayFromZero(owner, toDouble() * THOUSANDTHS).coerceIn(0, THOUSANDTHS.toLong())

fun Long.fromThousandths(): Float = (toDouble() / THOUSANDTHS).toFloat()

/** A distance in meters as whole centimeters, and 0 if it's negative. */
fun Float.toCentimeters(owner: String): Long = roundHalfAwayFromZero(owner, toDouble() * CENTIMETERS_PER_METER).coerceAtLeast(0)

fun Long.centimetersToMeters(): Float = (toDouble() / CENTIMETERS_PER_METER).toFloat()

/** A bearing in degrees as whole hundredths of a degree, taken modulo a full turn into [0, 36000). */
fun Double.toBearingHundredths(owner: String): Long {
    val hundredths = roundHalfAwayFromZero(owner, this * HUNDREDTHS_PER_DEGREE)
    return ((hundredths % FULL_TURN_HUNDREDTHS) + FULL_TURN_HUNDREDTHS) % FULL_TURN_HUNDREDTHS
}

fun Long.hundredthsToBearing(): Double = toDouble() / HUNDREDTHS_PER_DEGREE

private const val THOUSANDTHS = 1000.0
private const val CENTIMETERS_PER_METER = 100.0
private const val HUNDREDTHS_PER_DEGREE = 100.0
private const val FULL_TURN_HUNDREDTHS = 36_000L
