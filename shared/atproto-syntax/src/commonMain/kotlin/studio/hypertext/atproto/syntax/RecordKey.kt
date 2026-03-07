package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val recordKeyRegex: Regex = Regex("^[A-Za-z0-9._:~-]{1,512}$")

/**
 * Record key segment used in AT URIs.
 *
 * @property value Raw record key string.
 */
@JvmInline
@Serializable
public value class RecordKey(
    public val value: String,
) {
    init {
        validateRecordKey(value)
    }

    /**
     * Returns the raw record key string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [RecordKey].
     */
    public companion object {
        /**
         * Parses [value] into a [RecordKey], returning failures as [Result].
         */
        public fun parse(value: String): Result<RecordKey> = runCatching { RecordKey(value) }

        /**
         * Parses [value] into a [RecordKey] or throws [InvalidRecordKeyException].
         */
        public fun require(value: String): RecordKey = RecordKey(value)

        /**
         * Returns `true` when [value] can be parsed as a [RecordKey].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess
    }
}

private fun validateRecordKey(value: String) {
    if (value == "." || value == ".." || !recordKeyRegex.matches(value)) {
        throw InvalidRecordKeyException(value)
    }
}
