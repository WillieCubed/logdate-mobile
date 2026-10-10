package app.logdate.client.media.storage

/**
 * What a piece of LogDate-owned media is for, which decides the directory each platform keeps it in.
 *
 * See `docs/reference/media-references.md` for where each platform keeps each collection.
 */
enum class MediaCollection(
    /** The collection's name in a `logdate-media://` reference. */
    val id: String,
) {
    /** Photos and videos LogDate keeps for entries: imported, captured, downloaded by sync, or restored. */
    Library("library"),

    /** Voice recordings LogDate captured on this device or received from a paired watch. */
    Recordings("recordings"),
    ;

    companion object {
        /** The collection named [id], matched without regard to case, or `null` if there is none. */
        fun fromId(id: String): MediaCollection? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * A file LogDate keeps, named by its [collection] and its [path] inside that collection's directory.
 *
 * Its string form, `logdate-media://<collection>/<path>`, is what LogDate stores in place of an
 * absolute file path, so the reference keeps working when the operating system moves the app's
 * storage. [path] uses `/` between folders and is percent-encoded segment by segment in the string
 * form. It can never be empty, start with `/`, or contain an empty, `.` or `..` segment, so a
 * reference cannot point outside its collection.
 *
 * See `docs/reference/media-references.md`.
 */
data class LocalMediaRef(
    val collection: MediaCollection,
    val path: String,
) {
    init {
        require(path.split('/').all(::isSafeSegment)) { "Not a path inside a collection: $path" }
    }

    override fun toString(): String = "$SCHEME://${collection.id}/${path.split('/').joinToString("/", transform = ::percentEncodeSegment)}"

    companion object {
        /** The URI scheme of every [LocalMediaRef]. Distinct from `logdate://`, which opens screens. */
        const val SCHEME: String = "logdate-media"

        /** The reference [value] spells, or `null` when [value] is not a valid `logdate-media://` reference. */
        fun parse(value: String): LocalMediaRef? {
            val prefix = "$SCHEME://"
            if (!value.startsWith(prefix, ignoreCase = true)) return null
            val rest = value.substring(prefix.length)
            if ('?' in rest || '#' in rest) return null

            val collection = MediaCollection.fromId(rest.substringBefore('/', missingDelimiterValue = "")) ?: return null
            val segments = rest.substringAfter('/').split('/').map(::percentDecode)
            if (!segments.all(::isSafeSegment)) return null
            return LocalMediaRef(collection, segments.joinToString("/"))
        }
    }
}

private fun isSafeSegment(segment: String): Boolean =
    segment.isNotEmpty() &&
        segment != "." &&
        segment != ".." &&
        '/' !in segment &&
        '\\' !in segment &&
        '\u0000' !in segment

/** Characters a URI path segment may carry as written (RFC 3986 `pchar`, apart from `%`). */
private const val SEGMENT_SAFE_PUNCTUATION = "-._~!$&'()*+,;=:@"
private const val HEX_DIGITS = "0123456789ABCDEF"

private fun percentEncodeSegment(segment: String): String =
    buildString {
        segment.encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 0x80 && (char.isLetterOrDigit() || char in SEGMENT_SAFE_PUNCTUATION)) {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[value shr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

/** Decodes `%XX` escapes as UTF-8, leaving anything that is not a valid escape as written. */
internal fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var literalStart = 0
    var index = 0
    while (index < value.length) {
        val escaped = if (value[index] == '%') escapedByte(value, index) else null
        if (escaped == null) {
            index++
            continue
        }
        bytes += value.substring(literalStart, index).encodeToByteArray().asList()
        bytes += escaped
        index += 3
        literalStart = index
    }
    bytes += value.substring(literalStart).encodeToByteArray().asList()
    return bytes.toByteArray().decodeToString()
}

private fun escapedByte(
    value: String,
    percentIndex: Int,
): Byte? {
    if (percentIndex + 2 > value.lastIndex) return null
    val high = value[percentIndex + 1].digitToIntOrNull(16) ?: return null
    val low = value[percentIndex + 2].digitToIntOrNull(16) ?: return null
    return (high * 16 + low).toByte()
}
