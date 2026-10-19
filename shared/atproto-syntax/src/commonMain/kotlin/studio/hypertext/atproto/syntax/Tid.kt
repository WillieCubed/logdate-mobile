package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant

private val tidRegex: Regex = Regex("^[234567abcdefghij][234567abcdefghijklmnopqrstuvwxyz]{12}$")

/**
 * Timestamp identifier value used by AT Protocol repositories.
 *
 * @property value Raw TID string.
 */
@JvmInline
@Serializable
public value class Tid(
    public val value: String,
) {
    init {
        validateTid(value)
    }

    /**
     * Returns the raw TID string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [Tid].
     */
    public companion object {
        private const val ENCODED_LENGTH: Int = 13

        /**
         * Parses [value] into a [Tid], returning failures as [Result].
         */
        public fun parse(value: String): Result<Tid> = runCatching { Tid(value) }

        /**
         * Parses [value] into a [Tid] or throws [InvalidTidException].
         */
        public fun require(value: String): Tid = Tid(value)

        /**
         * Returns `true` when [value] can be parsed as a [Tid].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess

        /**
         * Encodes a non-negative [value] into a stable [Tid].
         */
        public fun fromLong(value: Long): Tid {
            require(value >= 0L) { "TID values must be non-negative" }
            var remaining = value.toULong()
            val output = CharArray(ENCODED_LENGTH)
            for (index in ENCODED_LENGTH - 1 downTo 0) {
                output[index] = TID_ALPHABET[(remaining and TID_DIGIT_MASK).toInt()]
                remaining = remaining shr TID_BITS_PER_DIGIT
            }
            return Tid(output.concatToString())
        }

        /**
         * Builds the TID for [instant] and [clockId], as the AT Protocol specifies them.
         *
         * A TID is a 64-bit number: the top bit is always zero, the next 53 bits count microseconds
         * since the Unix epoch, and the low 10 bits are a clock identifier that tells apart TIDs
         * made in the same microsecond. Because the timestamp sits in the high bits, a TID from a
         * later time sorts after an earlier one, both as a number and as a string.
         *
         * @throws IllegalArgumentException when [instant] is before the epoch or [clockId] is
         * outside `0..1023`.
         */
        public fun fromInstant(
            instant: Instant,
            clockId: Int = 0,
        ): Tid {
            require(clockId in 0..MAX_CLOCK_ID) { "TID clock identifiers must be in 0..$MAX_CLOCK_ID, got $clockId" }
            val epochMicros = instant.epochSeconds * MICROS_PER_SECOND + instant.nanosecondsOfSecond / NANOS_PER_MICRO
            require(epochMicros in 0..MAX_TIMESTAMP_MICROS) { "TID timestamps must fall between 1970 and 2255, got $instant" }
            return fromLong((epochMicros shl CLOCK_ID_BITS) or clockId.toLong())
        }
    }

    /**
     * Microseconds since the Unix epoch that this TID records.
     */
    public val timestampMicros: Long
        get() = toLong() ushr CLOCK_ID_BITS

    /**
     * Clock identifier stored in the low 10 bits of this TID.
     */
    public val clockId: Int
        get() = (toLong() and MAX_CLOCK_ID.toLong()).toInt()

    /**
     * Decodes this TID into the numeric value used to create it.
     */
    public fun toLong(): Long {
        var value = 0UL
        this.value.forEach { character ->
            val digit = tidAlphabetIndex[character] ?: throw InvalidTidException(this.value)
            value = (value shl TID_BITS_PER_DIGIT) or digit.toULong()
        }
        return value.toLong()
    }
}

private fun validateTid(value: String) {
    if (!tidRegex.matches(value)) {
        throw InvalidTidException(value)
    }
}

private const val CLOCK_ID_BITS: Int = 10
private const val MAX_CLOCK_ID: Int = (1 shl CLOCK_ID_BITS) - 1
private const val MAX_TIMESTAMP_MICROS: Long = Long.MAX_VALUE ushr CLOCK_ID_BITS
private const val MICROS_PER_SECOND: Long = 1_000_000L
private const val NANOS_PER_MICRO: Int = 1_000
private const val TID_BITS_PER_DIGIT: Int = 5
private const val TID_DIGIT_MASK: ULong = 31UL
private const val TID_ALPHABET: String = "234567abcdefghijklmnopqrstuvwxyz"
private val tidAlphabetIndex: Map<Char, Int> = TID_ALPHABET.withIndex().associate { (index, character) -> character to index }
