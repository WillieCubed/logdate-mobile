package app.logdate.server.identity

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encrypts private keys the server stores, using AES-GCM under a key derived from a deployment
 * secret (the key-encryption key).
 *
 * The encoded form is Base64 of the 12-byte IV followed by the ciphertext and authentication tag.
 * A value can only be decrypted with the same key-encryption key that encrypted it.
 */
@OptIn(ExperimentalEncodingApi::class)
class AtRestKeyCipher(
    encryptionKeySeed: String,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val aesKey =
        SecretKeySpec(
            MessageDigest.getInstance(SHA_256_ALGORITHM).digest(encryptionKeySeed.toByteArray()),
            AES_ALGORITHM,
        )

    fun encrypt(plaintext: ByteArray): String {
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return Base64.encode(iv + cipher.doFinal(plaintext))
    }

    /**
     * Decrypts [encoded]. Throws [javax.crypto.AEADBadTagException] when it was encrypted with a
     * different key-encryption key or has been altered.
     */
    fun decrypt(encoded: String): ByteArray {
        val bytes = Base64.decode(encoded)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_SIZE))
        return cipher.doFinal(bytes, GCM_IV_SIZE, bytes.size - GCM_IV_SIZE)
    }

    private companion object {
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val SHA_256_ALGORITHM = "SHA-256"
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}
