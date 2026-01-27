package app.logdate.server.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmCipher(private val secureRandom: SecureRandom = SecureRandom()) {
    fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(plaintext)
    }

    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext)
    }

    fun generateIV(): ByteArray {
        return ByteArray(IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }
    }

    private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(mode, keySpec, gcmSpec)
        return cipher
    }

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val IV_SIZE_BYTES = 12
    }
}
