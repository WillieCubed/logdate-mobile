package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val handleRegex: Regex =
    Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$")

/**
 * AT Protocol handle value.
 *
 * Handles are normalized to lowercase when parsed through [parse] or [require].
 *
 * @property value Raw handle string.
 */
@JvmInline
@Serializable
public value class Handle(
    public val value: String,
) {
    init {
        validateHandle(value)
    }

    /**
     * Lowercased canonical handle string.
     */
    public val normalized: String
        get() = value.lowercase()

    /**
     * Returns the normalized handle string.
     */
    override fun toString(): String = normalized

    /**
     * Factory helpers for [Handle].
     */
    public companion object {
        /**
         * Parses [value] into a normalized [Handle], returning failures as [Result].
         */
        public fun parse(value: String): Result<Handle> = runCatching { Handle(value.lowercase()) }

        /**
         * Parses [value] into a normalized [Handle] or throws [InvalidHandleException].
         */
        public fun require(value: String): Handle = Handle(value.lowercase())

        /**
         * Returns `true` when [value] can be parsed as a [Handle].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess
    }
}

private fun validateHandle(value: String) {
    if (value.isEmpty() || value.length > 253) {
        throw InvalidHandleException(value)
    }
    if (!handleRegex.matches(value)) {
        throw InvalidHandleException(value)
    }
}
