package app.logdate.client.sync.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class, DelicateCryptographyApi::class)
actual class AesGcmMediaPayloadCrypto actual constructor(
    key: ByteArray,
) : MediaPayloadCrypto {
    private val keyBytes = key.copyOf()
    private val aesGcm = CryptographyProvider.Default.get(AES.GCM)

    init {
        require(keyBytes.size in setOf(16, 24, 32)) {
            "Media encryption key must be 16, 24, or 32 bytes."
        }
    }

    actual override suspend fun encrypt(data: ByteArray): ByteArray {
        if (data.hasClientMediaPrefix()) return data
        val iv = ByteArray(CLIENT_MEDIA_IV_SIZE_BYTES)
        SecRandomCopyBytes(kSecRandomDefault, iv.size.toULong(), iv.refTo(0))
        val cipherText =
            aesGcm
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
                .cipher()
                .encryptWithIv(iv = iv, plaintext = data)
        return CLIENT_MEDIA_PREFIX_BYTES + iv + cipherText
    }

    actual override suspend fun decrypt(data: ByteArray): ByteArray {
        if (!data.hasClientMediaPrefix()) return data
        require(data.size > CLIENT_MEDIA_PREFIX_BYTES.size + CLIENT_MEDIA_IV_SIZE_BYTES) {
            "Encrypted media payload is too short."
        }
        val ivStart = CLIENT_MEDIA_PREFIX_BYTES.size
        val ivEnd = ivStart + CLIENT_MEDIA_IV_SIZE_BYTES
        val iv = data.copyOfRange(ivStart, ivEnd)
        val cipherText = data.copyOfRange(ivEnd, data.size)
        return aesGcm
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
            .cipher()
            .decryptWithIv(iv = iv, ciphertext = cipherText)
    }
}
