package app.logdate.client.sync.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class AesGcmMediaPayloadCrypto actual constructor(key: ByteArray) : MediaPayloadCrypto {
    private val keyBytes = key.copyOf()
    private val secureRandom = SecureRandom()

    init {
        require(keyBytes.size in setOf(16, 24, 32)) {
            "Media encryption key must be 16, 24, or 32 bytes."
        }
    }

    actual override suspend fun encrypt(data: ByteArray): ByteArray {
        if (data.hasClientMediaPrefix()) return data
        val iv = ByteArray(CLIENT_MEDIA_IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = cipher(Cipher.ENCRYPT_MODE, iv)
        val cipherText = cipher.doFinal(data)
        val output = ByteArray(CLIENT_MEDIA_PREFIX_BYTES.size + iv.size + cipherText.size)
        System.arraycopy(CLIENT_MEDIA_PREFIX_BYTES, 0, output, 0, CLIENT_MEDIA_PREFIX_BYTES.size)
        System.arraycopy(iv, 0, output, CLIENT_MEDIA_PREFIX_BYTES.size, iv.size)
        System.arraycopy(cipherText, 0, output, CLIENT_MEDIA_PREFIX_BYTES.size + iv.size, cipherText.size)
        return output
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
        val cipher = cipher(Cipher.DECRYPT_MODE, iv)
        return cipher.doFinal(cipherText)
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(keyBytes, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(mode, keySpec, gcmSpec)
        return cipher
    }

    private companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_TAG_BITS = 128
    }
}
