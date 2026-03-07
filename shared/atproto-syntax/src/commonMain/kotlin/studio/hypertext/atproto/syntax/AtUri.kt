package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * AT Protocol repository URI.
 *
 * Authorities are normalized to lowercase for handles while DID authorities are preserved.
 *
 * @property value Canonical AT URI string.
 */
@JvmInline
@Serializable
public value class AtUri(
    public val value: String,
) {
    init {
        parseComponents(value)
    }

    /**
     * Repository authority, either a DID or normalized handle.
     */
    public val authority: String
        get() = parseComponents(value).authority

    /**
     * Optional collection NSID segment.
     */
    public val collection: Nsid?
        get() = parseComponents(value).collection

    /**
     * Optional record key segment.
     */
    public val recordKey: RecordKey?
        get() = parseComponents(value).recordKey

    /**
     * Returns the canonical AT URI string.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [AtUri].
     */
    public companion object {
        /**
         * Parses [value] into a normalized [AtUri], returning failures as [Result].
         */
        public fun parse(value: String): Result<AtUri> =
            runCatching {
                val normalized: String = parseComponents(value).normalizedValue
                AtUri(normalized)
            }

        /**
         * Parses [value] into a normalized [AtUri] or throws [InvalidAtUriException].
         */
        public fun require(value: String): AtUri = parse(value).getOrThrow()

        /**
         * Returns `true` when [value] can be parsed as an [AtUri].
         */
        public fun isValid(value: String): Boolean = parse(value).isSuccess
    }
}

private data class AtUriComponents(
    val normalizedValue: String,
    val authority: String,
    val collection: Nsid?,
    val recordKey: RecordKey?,
)

private fun parseComponents(value: String): AtUriComponents {
    if (!value.startsWith("at://") || value.contains('?') || value.contains('#')) {
        throw InvalidAtUriException(value)
    }

    val withoutScheme: String = value.removePrefix("at://")
    if (withoutScheme.isEmpty() || withoutScheme.endsWith('/')) {
        throw InvalidAtUriException(value)
    }

    val segments: List<String> = withoutScheme.split('/')
    if (segments.any { it.isEmpty() } || segments.size > 3) {
        throw InvalidAtUriException(value)
    }

    val authority: String =
        if (segments.first().startsWith("did:")) {
            Did.require(segments.first()).value
        } else {
            Handle.require(segments.first()).normalized
        }

    val collection: Nsid? = segments.getOrNull(1)?.let(Nsid::require)
    val recordKey: RecordKey? = segments.getOrNull(2)?.let(RecordKey::require)
    if (recordKey != null && collection == null) {
        throw InvalidAtUriException(value)
    }

    val normalized: String =
        buildString {
            append("at://")
            append(authority)
            collection?.let {
                append('/')
                append(it.value)
            }
            recordKey?.let {
                append('/')
                append(it.value)
            }
        }

    return AtUriComponents(
        normalizedValue = normalized,
        authority = authority,
        collection = collection,
        recordKey = recordKey,
    )
}
