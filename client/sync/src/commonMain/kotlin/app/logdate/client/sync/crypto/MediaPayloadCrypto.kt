package app.logdate.client.sync.crypto

import app.logdate.client.media.MediaFileSource
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal const val CLIENT_MEDIA_PREFIX = "LDCE1"
internal val CLIENT_MEDIA_PREFIX_BYTES = CLIENT_MEDIA_PREFIX.encodeToByteArray()
internal const val CLIENT_MEDIA_IV_SIZE_BYTES = 12

internal fun ByteArray.hasClientMediaPrefix(): Boolean {
    if (size < CLIENT_MEDIA_PREFIX_BYTES.size) return false
    for (index in CLIENT_MEDIA_PREFIX_BYTES.indices) {
        if (this[index] != CLIENT_MEDIA_PREFIX_BYTES[index]) return false
    }
    return true
}

interface MediaPayloadCrypto {
    suspend fun encrypt(data: ByteArray): ByteArray

    suspend fun decrypt(data: ByteArray): ByteArray

    /**
     * Prepares a [MediaStreamEncryptor] for uploads. It writes the chunked LDCE2 format, which
     * [decrypt] reads alongside the single-shot LDCE1 format [encrypt] produces.
     */
    suspend fun streamEncryptor(): MediaStreamEncryptor
}

object NoOpMediaPayloadCrypto : MediaPayloadCrypto {
    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray = data

    override suspend fun streamEncryptor(): MediaStreamEncryptor = PassthroughMediaStreamEncryptor
}

expect class AesGcmMediaPayloadCrypto(
    key: ByteArray,
) : MediaPayloadCrypto {
    override suspend fun encrypt(data: ByteArray): ByteArray

    override suspend fun decrypt(data: ByteArray): ByteArray

    override suspend fun streamEncryptor(): MediaStreamEncryptor
}

/** Whether [this] media is already client-encrypted, judged from its header alone. */
internal fun MediaFileSource.isClientEncryptedMedia(): Boolean {
    if (sizeBytes < CLIENT_MEDIA_PREFIX_BYTES.size) return false
    val header = Buffer()
    open().use { source ->
        while (header.size < CLIENT_MEDIA_PREFIX_BYTES.size) {
            if (source.readAtMostTo(header, CLIENT_MEDIA_PREFIX_BYTES.size - header.size) == -1L) break
        }
    }
    return header.readByteArray().isClientEncryptedMedia()
}
