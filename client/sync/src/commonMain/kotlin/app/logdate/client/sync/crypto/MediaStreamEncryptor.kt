package app.logdate.client.sync.crypto

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Encrypts a media payload as it is read, so an upload never holds the whole file in memory.
 *
 * [MediaPayloadCrypto.decrypt] reads back whatever this produces.
 */
interface MediaStreamEncryptor {
    /** Local receipt scope for this exact captured encryption key and format; never transmitted. */
    val receiptContext: String? get() = null

    /** Length of the stream [encrypt] produces for a payload of [plainSizeBytes] bytes. */
    fun encryptedSizeBytes(plainSizeBytes: Long): Long

    /** Wraps [source] so its bytes are encrypted as they are read. Closing the result closes [source]. */
    fun encrypt(source: RawSource): RawSource
}

/**
 * Decrypts a media payload as it is read, so a download never holds the whole file in memory.
 */
interface MediaStreamDecryptor {
    /**
     * Decrypts [source] into [sink] and returns the decrypted size. LDCE2 is decrypted a chunk at a
     * time. LDCE1 seals the whole file in one operation, so it is decrypted whole, and only up to
     * [MAX_WHOLE_PAYLOAD_BYTES]. Anything else is copied through as it is.
     *
     * @throws IllegalArgumentException when the payload fails authentication or can't be read.
     */
    suspend fun decrypt(
        source: Source,
        sink: Sink,
    ): Long

    companion object {
        /** The largest LDCE1 payload decrypted whole: the most the per-kind media endpoint ever accepted. */
        const val MAX_WHOLE_PAYLOAD_BYTES: Long = 31L * 1024 * 1024
    }
}

/**
 * [MediaStreamDecryptor] for client-encrypted media: LDCE2 under [mediaKey] a chunk at a time, and
 * every other format through [decryptWhole].
 */
internal class ClientMediaStreamDecryptor(
    private val mediaKey: ByteArray?,
    private val decryptWhole: suspend (ByteArray) -> ByteArray,
) : MediaStreamDecryptor {
    override suspend fun decrypt(
        source: Source,
        sink: Sink,
    ): Long {
        val prefix = Buffer().also { source.peek().readAtMostTo(it, CLIENT_MEDIA_PREFIX_BYTES.size.toLong()) }.readByteArray()
        if (mediaKey != null && prefix.hasChunkedMediaPrefix()) return decryptChunkedMediaStream(mediaKey, source, sink)
        if (!prefix.startsWith("LDCE".encodeToByteArray())) {
            var copied = 0L
            val buffer = Buffer()
            while (source.readAtMostTo(buffer, COPY_BYTES) != -1L) {
                copied += buffer.size
                sink.write(buffer, buffer.size)
            }
            return copied
        }
        val whole = Buffer()
        while (source.readAtMostTo(whole, COPY_BYTES) != -1L) {
            require(whole.size <= MediaStreamDecryptor.MAX_WHOLE_PAYLOAD_BYTES) {
                "Encrypted media in a format decrypted whole is too large to decrypt on this device."
            }
        }
        val plaintext = decryptWhole(whole.readByteArray())
        sink.write(plaintext)
        return plaintext.size.toLong()
    }

    private companion object {
        const val COPY_BYTES = 64L * 1024
    }
}

internal object PassthroughMediaStreamEncryptor : MediaStreamEncryptor {
    override val receiptContext: String = "passthrough-v1"

    override fun encryptedSizeBytes(plainSizeBytes: Long): Long = plainSizeBytes

    override fun encrypt(source: RawSource): RawSource = source
}
