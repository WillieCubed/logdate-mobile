package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val datetimeRegex: Regex =
    Regex(
        "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):[0-5]\\d:([0-5]\\d|60)(\\.\\d+)?(Z|[+-]([01]\\d|2[0-3]):[0-5]\\d)$",
    )

/**
 * A timestamp in the AT Protocol's `datetime` form: RFC 3339 with an uppercase `T`, and a time zone
 * that is either `Z` or a numeric offset. `-00:00`, RFC 3339's "local time unknown", isn't allowed.
 *
 * @property value Raw datetime string.
 */
@JvmInline
@Serializable
public value class Datetime(
    public val value: String,
) {
    init {
        if (!datetimeRegex.matches(value) || value.endsWith("-00:00")) throw InvalidDatetimeException(value)
    }

    /**
     * Returns the raw datetime string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [Datetime].
     */
    public companion object {
        /**
         * Parses [value] into a [Datetime], returning failures as [Result].
         */
        public fun parse(value: String): Result<Datetime> = runCatching { Datetime(value) }

        /**
         * Parses [value] into a [Datetime] or throws [InvalidDatetimeException].
         */
        public fun require(value: String): Datetime = Datetime(value)

        /**
         * Returns `true` when [value] can be parsed as a [Datetime].
         */
        public fun isValid(value: String): Boolean = datetimeRegex.matches(value) && !value.endsWith("-00:00")
    }
}
