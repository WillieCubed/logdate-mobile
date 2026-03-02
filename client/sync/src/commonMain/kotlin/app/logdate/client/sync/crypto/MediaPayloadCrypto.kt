package app.logdate.client.sync.crypto

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
}

object NoOpMediaPayloadCrypto : MediaPayloadCrypto {
    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray = data
}

expect class AesGcmMediaPayloadCrypto(
    key: ByteArray,
) : MediaPayloadCrypto {
    override suspend fun encrypt(data: ByteArray): ByteArray

    override suspend fun decrypt(data: ByteArray): ByteArray
}
