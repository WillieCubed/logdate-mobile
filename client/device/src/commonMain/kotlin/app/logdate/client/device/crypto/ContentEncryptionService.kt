@file:OptIn(ExperimentalEncodingApi::class)

package app.logdate.client.device.crypto

import io.github.aakira.napier.Napier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encrypts and decrypts user content using the identity key.
 *
 * This service provides the high-level API for E2EE:
 * - Plaintext strings → encrypted envelopes
 * - Encrypted envelopes → plaintext strings
 *
 * The encryption is transparent to the application layer.
 */
class ContentEncryptionService(
    private val identityKeyManager: IdentityKeyManager,
    private val keyDerivation: KeyDerivation,
    private val cryptoManager: CryptoManager,
) {
    /**
     * Encrypts a string of plaintext content.
     *
     * @param contentId Unique identifier for this content (e.g., journal entry UUID)
     * @param plaintext The content to encrypt
     * @return Encrypted envelope containing ciphertext + metadata
     * @throws IdentityKeyNotFoundException if identity not set up
     */
    suspend fun encryptContent(
        contentId: String,
        plaintext: String,
    ): EncryptedEnvelope {
        val identityKey = identityKeyManager.getIdentityKey()
        val contentKey = keyDerivation.deriveKey(identityKey, CONTEXT_CONTENT, contentId)

        val iv = cryptoManager.generateRandomBytes(12)
        val aad = buildAAD(contentId)
        val plaintextBytes = plaintext.encodeToByteArray()

        val ciphertext = cryptoManager.aesGcmEncrypt(contentKey, iv, aad, plaintextBytes)

        return EncryptedEnvelope(
            version = ENVELOPE_VERSION,
            algorithm = ALGORITHM_AES_GCM,
            iv = iv.encodeBase64(),
            ciphertext = ciphertext.encodeBase64(),
            aad = aad.encodeBase64(),
        ).also {
            Napier.d("Content encrypted: $contentId (${plaintext.length} bytes → ${it.ciphertext.length} bytes)")
        }
    }

    /**
     * Decrypts an encrypted envelope back to plaintext.
     *
     * @param contentId The content identifier (must match what was used during encryption)
     * @param envelope The encrypted envelope
     * @return Decrypted plaintext content
     * @throws IllegalArgumentException if envelope format is invalid
     * @throws Exception if decryption fails (wrong key, tampered data, etc)
     * @throws IdentityKeyNotFoundException if identity not set up
     */
    suspend fun decryptContent(
        contentId: String,
        envelope: EncryptedEnvelope,
    ): String {
        require(envelope.version == ENVELOPE_VERSION) {
            "Unsupported envelope version: ${envelope.version}"
        }
        require(envelope.algorithm == ALGORITHM_AES_GCM) {
            "Unsupported algorithm: ${envelope.algorithm}"
        }

        val identityKey = identityKeyManager.getIdentityKey()
        val contentKey = keyDerivation.deriveKey(identityKey, CONTEXT_CONTENT, contentId)

        val iv = envelope.iv.decodeBase64()
        val ciphertext = envelope.ciphertext.decodeBase64()
        val aad = envelope.aad.decodeBase64()

        val plaintext = cryptoManager.aesGcmDecrypt(contentKey, iv, aad, ciphertext)

        return plaintext.decodeToString().also {
            Napier.d("Content decrypted: $contentId (${envelope.ciphertext.length} bytes → ${it.length} bytes)")
        }
    }

    /**
     * Builds the additional authenticated data (AAD) for encryption.
     *
     * AAD is not encrypted but is authenticated, preventing attackers from
     * swapping encrypted values between different content IDs.
     */
    private fun buildAAD(contentId: String): ByteArray = "type=CONTENT|v=$ENVELOPE_VERSION|id=$contentId".encodeToByteArray()

    companion object {
        private const val ENVELOPE_VERSION = 1
        private const val ALGORITHM_AES_GCM = "AES-GCM"
        private const val CONTEXT_CONTENT = "journal_content"
    }
}

/**
 * Encrypted content with all necessary metadata for decryption.
 *
 * This is what gets stored on disk or synced to the server.
 * The server sees only the ciphertext and metadata - never the plaintext.
 */
@Serializable
data class EncryptedEnvelope(
    @SerialName("v")
    val version: Int,
    @SerialName("alg")
    val algorithm: String,
    @SerialName("iv")
    val iv: String,
    @SerialName("ct")
    val ciphertext: String,
    @SerialName("aad")
    val aad: String,
)

private fun String.decodeBase64(): ByteArray = Base64.decode(this)

private fun ByteArray.encodeBase64(): String = Base64.encode(this)
