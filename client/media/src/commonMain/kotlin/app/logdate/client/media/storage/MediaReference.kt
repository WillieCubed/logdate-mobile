package app.logdate.client.media.storage

/**
 * What a stored media string refers to, for code that needs to branch on it.
 *
 * Stored strings come in many spellings: `logdate-media://` references, `file:///` and `file:/`
 * URIs (percent-encoded or not), bare absolute paths, and URIs LogDate does not own. [parse]
 * sorts them into the three cases below. To open a file, use [MediaFileResolver] instead.
 */
sealed interface MediaReference {
    /** A file LogDate keeps in one of its collections. */
    data class Owned(
        val ref: LocalMediaRef,
    ) : MediaReference

    /**
     * An absolute file path, written by code from before `logdate-media://` references existed.
     *
     * [path] is percent-decoded and uses `/` separators. Earlier builds built some file URIs by
     * prefixing `file://` to an unencoded path, so when decoding changed the path, [alternatePath]
     * holds it as written; a name such as `a%20b.jpg` may be either spelling.
     */
    data class LocalFile(
        val path: String,
        val alternatePath: String? = null,
    ) : MediaReference

    /** Anything else: a Photos asset, a content provider, a remote URL, or a malformed string. */
    data class External(
        val uri: String,
    ) : MediaReference

    companion object {
        fun parse(value: String): MediaReference {
            LocalMediaRef.parse(value)?.let { return Owned(it) }
            return localFile(value) ?: External(value)
        }
    }
}

private fun localFile(value: String): MediaReference.LocalFile? {
    val written =
        when {
            value.startsWith("/") -> return MediaReference.LocalFile(value)
            value.isDriveLetterPath() -> return MediaReference.LocalFile(value.replace('\\', '/'))
            value.startsWith("file:", ignoreCase = true) -> fileUriPath(value.substring("file:".length)) ?: return null
            else -> return null
        }
    val decoded = percentDecode(written)
    return if (decoded == written) MediaReference.LocalFile(written) else MediaReference.LocalFile(decoded, alternatePath = written)
}

/** The path in the part of a `file:` URI after the scheme, or `null` when it names another host. */
private fun fileUriPath(afterScheme: String): String? {
    val path =
        when {
            afterScheme.startsWith("///") -> afterScheme.substring(2)
            afterScheme.startsWith("//localhost/", ignoreCase = true) -> afterScheme.substring("//localhost".length)
            afterScheme.startsWith("/") && !afterScheme.startsWith("//") -> afterScheme
            else -> return null
        }
    val windowsPath = path.substring(1)
    return if (windowsPath.isDriveLetterPath()) windowsPath else path
}

private fun String.isDriveLetterPath(): Boolean = length > 2 && this[0].isLetter() && this[1] == ':' && (this[2] == '/' || this[2] == '\\')
