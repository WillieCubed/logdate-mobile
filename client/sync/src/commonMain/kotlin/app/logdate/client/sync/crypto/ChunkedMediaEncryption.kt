package app.logdate.client.sync.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray

/*
 * LDCE2: media encrypted in independently sealed chunks, so neither side ever holds more than one
 * chunk. LDCE1 seals the whole file in one AES-GCM operation, and Android's AES-GCM (Conscrypt)
 * buffers every input byte until the operation finishes, so an LDCE1 upload always costs the
 * whole file in memory there.
 *
 * Layout:
 *   header  = "LDCE2" | chunk size (u32, big-endian) | salt (16) | nonce prefix (7)
 *   chunk i = AES-GCM(fileKey, nonce = prefix | i (u32, big-endian) | last (1), aad = header)
 *
 * fileKey is HKDF-SHA256(media key, salt, "LDCE2 media"), so nonces never repeat across files
 * under one key. Every chunk but the last carries exactly `chunk size` plaintext bytes; the last
 * carries the rest (none for an empty file) and is the only one flagged last. The counter stops
 * chunks being reordered, the flag stops the file being truncated at a chunk boundary, and the
 * header as associated data stops it being altered.
 */

internal val CHUNKED_MEDIA_PREFIX_BYTES = "LDCE2".encodeToByteArray()
private const val CHUNK_SIZE_BYTES = 64 * 1024

/** Largest chunk size a payload may declare, so a corrupt header cannot demand a huge allocation. */
private const val MAX_CHUNK_SIZE_BYTES = 16 * 1024 * 1024
private const val SALT_SIZE_BYTES = 16
private const val NONCE_PREFIX_SIZE_BYTES = 7
private const val TAG_SIZE_BYTES = 16
private const val FILE_KEY_SIZE_BYTES = 32
private val HEADER_SIZE_BYTES = CHUNKED_MEDIA_PREFIX_BYTES.size + Int.SIZE_BYTES + SALT_SIZE_BYTES + NONCE_PREFIX_SIZE_BYTES
private val FILE_KEY_INFO = "LDCE2 media".encodeToByteArray()

internal fun ByteArray.hasChunkedMediaPrefix(): Boolean = startsWith(CHUNKED_MEDIA_PREFIX_BYTES)

/** Whether [this] is already client-encrypted media in any format, and must not be encrypted again. */
internal fun ByteArray.isClientEncryptedMedia(): Boolean = hasClientMediaPrefix() || hasChunkedMediaPrefix()

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

/** Streams media into the LDCE2 format under [mediaKey]. */
internal class ChunkedMediaStreamEncryptor(
    private val mediaKey: ByteArray,
) : MediaStreamEncryptor {
    override fun encryptedSizeBytes(plainSizeBytes: Long): Long {
        val chunks = maxOf(1L, (plainSizeBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES)
        return HEADER_SIZE_BYTES + plainSizeBytes + chunks * TAG_SIZE_BYTES
    }

    override fun encrypt(source: RawSource): RawSource {
        val salt = CryptographyRandom.nextBytes(SALT_SIZE_BYTES)
        val noncePrefix = CryptographyRandom.nextBytes(NONCE_PREFIX_SIZE_BYTES)
        val header = header(CHUNK_SIZE_BYTES, salt, noncePrefix)
        return ChunkEncryptingSource(
            plaintext = source.buffered(),
            header = header,
            cipher = fileCipher(mediaKey, salt),
            noncePrefix = noncePrefix,
        )
    }
}

/** Decrypts a whole LDCE2 payload. Throws when any chunk fails authentication or the layout is wrong. */
internal fun decryptChunkedMedia(
    mediaKey: ByteArray,
    data: ByteArray,
): ByteArray {
    require(data.size >= HEADER_SIZE_BYTES + TAG_SIZE_BYTES) { "Encrypted media payload is too short." }
    val header = data.copyOfRange(0, HEADER_SIZE_BYTES)
    var offset = CHUNKED_MEDIA_PREFIX_BYTES.size
    val chunkSize = data.readIntAt(offset)
    require(chunkSize in 1..MAX_CHUNK_SIZE_BYTES) { "Encrypted media payload declares an invalid chunk size." }
    offset += Int.SIZE_BYTES
    val salt = data.copyOfRange(offset, offset + SALT_SIZE_BYTES)
    offset += SALT_SIZE_BYTES
    val noncePrefix = data.copyOfRange(offset, offset + NONCE_PREFIX_SIZE_BYTES)
    offset += NONCE_PREFIX_SIZE_BYTES

    val cipher = fileCipher(mediaKey, salt)
    val plaintext = Buffer()
    var index = 0
    while (true) {
        val remaining = data.size - offset
        val sealedChunkSize = chunkSize + TAG_SIZE_BYTES
        val last = remaining <= sealedChunkSize
        val length = if (last) remaining else sealedChunkSize
        require(length >= TAG_SIZE_BYTES) { "Encrypted media payload ends mid-chunk." }
        val sealed = data.copyOfRange(offset, offset + length)
        plaintext.write(cipher.decryptChunk(noncePrefix, index, last, sealed, header))
        offset += length
        if (last) return plaintext.readByteArray()
        index++
    }
}

private class ChunkEncryptingSource(
    private val plaintext: Source,
    private val header: ByteArray,
    private val cipher: IvAuthenticatedCipher,
    private val noncePrefix: ByteArray,
) : RawSource {
    private val pending = Buffer().apply { write(header) }
    private var index = 0
    private var finished = false

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (pending.exhausted()) {
            if (finished) return -1
            sealNextChunk()
        }
        return pending.readAtMostTo(sink, byteCount)
    }

    private fun sealNextChunk() {
        val chunk = Buffer()
        while (chunk.size < CHUNK_SIZE_BYTES) {
            if (plaintext.readAtMostTo(chunk, CHUNK_SIZE_BYTES - chunk.size) == -1L) break
        }
        val last = plaintext.exhausted()
        pending.write(cipher.encryptChunk(noncePrefix, index, last, chunk.readByteArray(), header))
        index++
        finished = last
    }

    override fun close() {
        plaintext.close()
    }
}

@OptIn(DelicateCryptographyApi::class)
private fun IvAuthenticatedCipher.encryptChunk(
    noncePrefix: ByteArray,
    index: Int,
    last: Boolean,
    plaintext: ByteArray,
    header: ByteArray,
): ByteArray = encryptWithIvBlocking(chunkNonce(noncePrefix, index, last), plaintext, header)

@OptIn(DelicateCryptographyApi::class)
private fun IvAuthenticatedCipher.decryptChunk(
    noncePrefix: ByteArray,
    index: Int,
    last: Boolean,
    sealed: ByteArray,
    header: ByteArray,
): ByteArray = decryptWithIvBlocking(chunkNonce(noncePrefix, index, last), sealed, header)

private fun fileCipher(
    mediaKey: ByteArray,
    salt: ByteArray,
): IvAuthenticatedCipher {
    val provider = CryptographyProvider.Default
    val fileKey =
        provider
            .get(HKDF)
            .secretDerivation(SHA256, FILE_KEY_SIZE_BYTES.bytes, salt, FILE_KEY_INFO)
            .deriveSecretToByteArrayBlocking(mediaKey)
    return provider
        .get(AES.GCM)
        .keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, fileKey)
        .cipher()
}

private fun header(
    chunkSize: Int,
    salt: ByteArray,
    noncePrefix: ByteArray,
): ByteArray =
    Buffer()
        .apply {
            write(CHUNKED_MEDIA_PREFIX_BYTES)
            writeInt(chunkSize)
            write(salt)
            write(noncePrefix)
        }.readByteArray()

private fun chunkNonce(
    noncePrefix: ByteArray,
    index: Int,
    last: Boolean,
): ByteArray =
    Buffer()
        .apply {
            write(noncePrefix)
            writeInt(index)
            writeByte(if (last) 1 else 0)
        }.readByteArray()

private fun ByteArray.readIntAt(offset: Int): Int = Buffer().apply { write(this@readIntAt, offset, offset + Int.SIZE_BYTES) }.readInt()
