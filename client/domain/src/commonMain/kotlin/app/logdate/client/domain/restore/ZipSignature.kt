package app.logdate.client.domain.restore

/**
 * Recognises a zip file from its first four bytes, so a file that cannot be one is turned down before
 * anything reads or copies the rest of it.
 */
object ZipSignature {
    const val LENGTH = 4

    private const val P = 'P'.code.toByte()
    private const val K = 'K'.code.toByte()
    private val KINDS = setOf<Byte>(3, 5, 7)

    /** Whether [header] begins a zip: a local file header, an empty archive, or a spanned archive marker. */
    fun isZip(header: ByteArray): Boolean =
        header.size >= LENGTH && header[0] == P && header[1] == K && header[2] in KINDS && header[3] == (header[2] + 1).toByte()
}
