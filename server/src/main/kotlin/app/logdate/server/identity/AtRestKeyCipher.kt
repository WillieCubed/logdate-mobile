package app.logdate.server.identity

import java.security.GeneralSecurityException
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
 * New values are always encrypted with [encryptionKeySeed]. Values can also be decrypted with any of
 * [decryptOnlySeeds], which lets a deployment move to a new key-encryption key: first every
 * instance learns to read the new key, then the new key becomes the encrypting one and [rewrap]
 * re-encrypts the stored values.
 */
@OptIn(ExperimentalEncodingApi::class)
class AtRestKeyCipher(
    encryptionKeySeed: String,
    decryptOnlySeeds: List<String> = emptyList(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val encryptionKey = aesKey(encryptionKeySeed)
    private val decryptOnlyKeys = decryptOnlySeeds.filter { it != encryptionKeySeed }.map(::aesKey)

    fun encrypt(plaintext: ByteArray): String {
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return Base64.encode(iv + cipher.doFinal(plaintext))
    }

    /**
     * Decrypts [encoded] with the encrypting key or any decrypt-only key. Throws
     * [javax.crypto.AEADBadTagException] when none of them encrypted it, or it has been altered.
     */
    fun decrypt(encoded: String): ByteArray = decryptWithAnyKey(Base64.decode(encoded)).plaintext

    /**
     * Returns [encoded] re-encrypted with the encrypting key, or null when the encrypting key
     * already encrypted it. Throws [javax.crypto.AEADBadTagException] when no known key can read it.
     */
    fun rewrap(encoded: String): String? {
        val decrypted = decryptWithAnyKey(Base64.decode(encoded))
        return if (decrypted.usedEncryptionKey) null else encrypt(decrypted.plaintext)
    }

    private fun decryptWithAnyKey(bytes: ByteArray): Decrypted {
        var failure: GeneralSecurityException? = null
        (listOf(encryptionKey) + decryptOnlyKeys).forEachIndexed { index, key ->
            try {
                return Decrypted(plaintext = decryptWith(key, bytes), usedEncryptionKey = index == 0)
            } catch (exception: GeneralSecurityException) {
                failure = exception
            }
        }
        throw checkNotNull(failure)
    }

    private fun decryptWith(
        key: SecretKeySpec,
        bytes: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_SIZE))
        return cipher.doFinal(bytes, GCM_IV_SIZE, bytes.size - GCM_IV_SIZE)
    }

    private class Decrypted(
        val plaintext: ByteArray,
        val usedEncryptionKey: Boolean,
    )

    private companion object {
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val SHA_256_ALGORITHM = "SHA-256"
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128

        fun aesKey(seed: String): SecretKeySpec =
            SecretKeySpec(MessageDigest.getInstance(SHA_256_ALGORITHM).digest(seed.toByteArray()), AES_ALGORITHM)
    }
}
