package app.logdate.atproto.syntax

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
    }
}

private fun validateTid(value: String) {
    if (!tidRegex.matches(value)) {
        throw InvalidTidException(value)
    }
}
