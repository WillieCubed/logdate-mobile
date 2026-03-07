package app.logdate.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Namespaced identifier used for AT Protocol lexicon and XRPC names.
 *
 * Authority segments are normalized to lowercase during parsing.
 *
 * @property value Canonical NSID string.
 */
@JvmInline
@Serializable
public value class Nsid(
    public val value: String,
) {
    init {
        validateNsid(value)
    }

    /**
     * Authority segment containing the reversed domain prefix.
     */
    public val authority: String
        get() = value.substringBeforeLast('.')

    /**
     * Final name segment.
     */
    public val name: String
        get() = value.substringAfterLast('.')

    /**
     * Returns the canonical NSID string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [Nsid].
     */
    public companion object {
        /**
         * Parses [value] into a normalized [Nsid], returning failures as [Result].
         */
        public fun parse(value: String): Result<Nsid> =
            runCatching {
                val normalized: String = normalizeNsid(value)
                Nsid(normalized)
            }

        /**
         * Parses [value] into a normalized [Nsid] or throws [InvalidNsidException].
         */
        public fun require(value: String): Nsid = parse(value).getOrThrow()

        /**
         * Returns `true` when [value] can be parsed as an [Nsid].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess
    }
}

private fun normalizeNsid(value: String): String {
    validateNsid(value)
    val segments: List<String> = value.split('.')
    val authoritySegments: List<String> = segments.dropLast(1).map { it.lowercase() }
    return (authoritySegments + segments.last()).joinToString(".")
}

private fun validateNsid(value: String) {
    if (value.isEmpty() || value.length > 317 || value.any { it.code > 0x7F }) {
        throw InvalidNsidException(value)
    }

    val segments: List<String> = value.split('.')
    if (segments.size < 3) {
        throw InvalidNsidException(value)
    }

    val authoritySegments: List<String> = segments.dropLast(1)
    val nameSegment: String = segments.last()
    if (authoritySegments.size < 2 || authoritySegments.joinToString(".").length > 253) {
        throw InvalidNsidException(value)
    }

    authoritySegments.forEachIndexed { index, segment ->
        if (segment.isEmpty() || segment.length > 63) {
            throw InvalidNsidException(value)
        }
        if (segment.first() == '-' || segment.last() == '-') {
            throw InvalidNsidException(value)
        }
        if (segment.any { !it.isLetterOrDigit() && it != '-' }) {
            throw InvalidNsidException(value)
        }
        if (index == 0 && segment.first().isDigit()) {
            throw InvalidNsidException(value)
        }
    }

    if (nameSegment.isEmpty() || nameSegment.length > 63 || nameSegment.first().isDigit()) {
        throw InvalidNsidException(value)
    }
    if (nameSegment.any { !it.isLetterOrDigit() }) {
        throw InvalidNsidException(value)
    }
}
