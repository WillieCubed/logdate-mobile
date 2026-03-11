package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

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
    }

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

private const val TID_BITS_PER_DIGIT: Int = 5
private const val TID_DIGIT_MASK: ULong = 31UL
private const val TID_ALPHABET: String = "234567abcdefghijklmnopqrstuvwxyz"
private val tidAlphabetIndex: Map<Char, Int> = TID_ALPHABET.withIndex().associate { (index, character) -> character to index }
