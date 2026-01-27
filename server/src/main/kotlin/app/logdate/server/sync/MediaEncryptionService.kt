package app.logdate.server.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaEncryptionService internal constructor(
    private val keyBytes: ByteArray?,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun encryptIfConfigured(data: ByteArray): ByteArray {
        if (keyBytes == null) return data
        if (data.hasPrefix(CLIENT_PREFIX_BYTES) || data.hasPrefix(SERVER_PREFIX_BYTES)) {
            return data
        }

        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = cipher(Cipher.ENCRYPT_MODE, iv)
        val cipherText = cipher.doFinal(data)
        val output = ByteArray(SERVER_PREFIX_BYTES.size + iv.size + cipherText.size)
        System.arraycopy(SERVER_PREFIX_BYTES, 0, output, 0, SERVER_PREFIX_BYTES.size)
        System.arraycopy(iv, 0, output, SERVER_PREFIX_BYTES.size, iv.size)
        System.arraycopy(cipherText, 0, output, SERVER_PREFIX_BYTES.size + iv.size, cipherText.size)
        return output
    }

    fun decryptIfNeeded(data: ByteArray): ByteArray {
        if (!data.hasPrefix(SERVER_PREFIX_BYTES)) return data

        require(keyBytes != null) { "MEDIA_ENCRYPTION_KEY is required to decrypt media payloads." }
        if (data.size <= SERVER_PREFIX_BYTES.size + IV_SIZE_BYTES) {
            throw IllegalArgumentException("Encrypted media payload is too short.")
        }

        val ivStart = SERVER_PREFIX_BYTES.size
        val ivEnd = ivStart + IV_SIZE_BYTES
        val iv = data.copyOfRange(ivStart, ivEnd)
        val cipherText = data.copyOfRange(ivEnd, data.size)
        val cipher = cipher(Cipher.DECRYPT_MODE, iv)
        return cipher.doFinal(cipherText)
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher {
        val key = requireNotNull(keyBytes) { "MEDIA_ENCRYPTION_KEY is not configured." }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(mode, keySpec, gcmSpec)
        return cipher
    }

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE_BYTES = 12
        private val SERVER_PREFIX_BYTES = "LDME1".toByteArray(Charsets.UTF_8)
        private val CLIENT_PREFIX_BYTES = "LDCE1".toByteArray(Charsets.UTF_8)

        fun fromEnvironment(): MediaEncryptionService {
            val rawKey = System.getenv("MEDIA_ENCRYPTION_KEY") ?: return MediaEncryptionService(null)
            val keyBytes = decodeKey(rawKey)
            return MediaEncryptionService(keyBytes)
        }

        internal fun fromKeyBytes(keyBytes: ByteArray): MediaEncryptionService {
            return MediaEncryptionService(keyBytes.copyOf())
        }

        internal fun clientPrefixBytes(): ByteArray = CLIENT_PREFIX_BYTES.copyOf()

        private fun decodeKey(rawKey: String): ByteArray {
            val decoded = try {
                Base64.getDecoder().decode(rawKey)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("MEDIA_ENCRYPTION_KEY must be base64-encoded.", e)
            }

            if (decoded.size !in setOf(16, 24, 32)) {
                throw IllegalArgumentException(
                    "MEDIA_ENCRYPTION_KEY must decode to 16, 24, or 32 bytes (AES-128/192/256)."
                )
            }

            return decoded
        }
    }
}
