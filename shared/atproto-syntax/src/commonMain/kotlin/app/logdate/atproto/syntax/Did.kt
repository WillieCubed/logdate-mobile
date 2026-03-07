package app.logdate.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val didMethodRegex: Regex = Regex("^[a-z0-9]+$")
private val didIdentifierCharRegex: Regex = Regex("^[A-Za-z0-9._:%-]+$")

/**
 * Generic decentralized identifier value.
 *
 * The parser enforces the broad `did:<method>:<identifier>` shape without restricting the
 * method to AT Protocol-specific values.
 *
 * @property value Raw DID string.
 */
@JvmInline
@Serializable
public value class Did(
    public val value: String,
) {
    init {
        validateDid(value)
    }

    /**
     * DID method segment.
     */
    public val method: String
        get() = value.substringAfter("did:").substringBefore(':')

    /**
     * DID identifier segment after the method prefix.
     */
    public val identifier: String
        get() = value.substringAfter("did:$method:")

    /**
     * Returns the raw DID string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [Did].
     */
    public companion object {
        /**
         * Parses [value] into a [Did], returning failures as [Result].
         */
        public fun parse(value: String): Result<Did> = runCatching { Did(value) }

        /**
         * Parses [value] into a [Did] or throws [InvalidDidException].
         */
        public fun require(value: String): Did = Did(value)

        /**
         * Returns `true` when [value] can be parsed as a [Did].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess
    }
}

private fun validateDid(value: String) {
    if (!value.startsWith("did:")) {
        throw InvalidDidException(value)
    }

    val parts: List<String> = value.split(':')
    if (parts.size < 3) {
        throw InvalidDidException(value)
    }

    val method: String = parts[1]
    val identifierParts: List<String> = parts.drop(2)
    if (!didMethodRegex.matches(method)) {
        throw InvalidDidException(value)
    }
    if (identifierParts.any { it.isEmpty() || !didIdentifierCharRegex.matches(it) }) {
        throw InvalidDidException(value)
    }
}
