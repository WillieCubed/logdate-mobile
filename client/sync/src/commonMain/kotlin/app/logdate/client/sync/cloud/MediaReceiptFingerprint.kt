package app.logdate.client.sync.cloud

import app.logdate.client.media.MediaFileSource
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.Buffer

/** Bounded-memory local scan, not a network upload. Includes metadata and captured encryption key. */
internal fun mediaReceiptFingerprint(
    media: MediaFileSource,
    encryptionContext: String,
): ReceiptFingerprint {
    val hasher = CryptographyProvider.Default.get(SHA256).hasher()
    val digest =
        hasher.createHashFunction().use { hash ->
            hash.updatingSource(SizeCheckedSource(media)).use { source ->
                val scratch = Buffer()
                while (source.readAtMostTo(scratch, 64 * 1024L) != -1L) scratch.clear()
            }
            hash.hashToByteArray()
        }
    val scope =
        "$encryptionContext:${media.sizeBytes}:${media.fileName.length}:${media.fileName}:${media.mimeType}:" +
            digest.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    return ReceiptFingerprint(
        hasher.hashBlocking(scope.encodeToByteArray()).joinToString("") { it.toUByte().toString(16).padStart(2, '0') },
        digest,
    )
}

internal data class ReceiptFingerprint(
    val value: String,
    val contentDigest: ByteArray,
)

/** Validate the bytes actually streamed before releasing the final chunk to the HTTP writer. */
internal class ReceiptVerifiedSource(
    media: MediaFileSource,
    private val expected: ByteArray,
) : kotlinx.io.RawSource {
    private val function =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .createHashFunction()
    private val source = function.updatingSource(SizeCheckedSource(media))
    private val size = media.sizeBytes
    private var read = 0L
    private var verified = false

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (byteCount == 0L) return 0
        val count = source.readAtMostTo(sink, byteCount)
        if (count > 0) read += count
        if (!verified && (read >= size || count == -1L)) {
            if (read != size || source.readAtMostTo(Buffer(), 1L) != -1L || !function.hashToByteArray().contentEquals(expected)) {
                throw MediaReadException("Media changed after its upload identity was prepared")
            }
            verified = true
        }
        return count
    }

    override fun close() {
        try {
            source.close()
        } finally {
            function.close()
        }
    }
}
