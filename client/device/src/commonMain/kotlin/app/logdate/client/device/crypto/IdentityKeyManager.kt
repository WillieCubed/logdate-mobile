package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable

/**
 * Manages the user's identity cryptographic key.
 *
 * The identity key is the root of all encryption for the user's data.
 * It's derived from a BIP-39 recovery phrase and stored securely in the device keystore.
 */
class IdentityKeyManager(
    private val secureStorage: SecureStorage,
    private val cryptoManager: CryptoManager,
) {
    /**
     * Checks if this device has already been set up with an identity key.
     */
    suspend fun hasIdentityKey(): Boolean = secureStorage.getBytes(KEY_IDENTITY_KEY) != null

    /**
     * Sets up a new identity for the first time.
     *
     * This generates a new recovery phrase and derives the identity key from it.
     * The recovery phrase is NOT stored - the user must write it down.
     *
     * @return The recovery phrase the user must store safely
     */
    suspend fun setupNewIdentity(): RecoveryPhrase {
        require(!hasIdentityKey()) { "Identity key already exists" }

        val phrase = cryptoManager.generateRecoveryPhrase()
        val identityKey = cryptoManager.deriveMasterKey(phrase)

        secureStorage.putBytes(KEY_IDENTITY_KEY, identityKey)
        Napier.d("New identity established")

        return RecoveryPhrase(phrase)
    }

    /**
     * Recovers the identity key using a previously saved recovery phrase.
     *
     * Used when the user is setting up on a new device or re-authenticating.
     *
     * @param phrase The 12-word recovery phrase
     * @throws IllegalArgumentException if phrase is invalid
     */
    suspend fun recoverIdentity(phrase: List<String>) {
        require(cryptoManager.validateRecoveryPhrase(phrase)) {
            "Invalid recovery phrase"
        }

        val identityKey = cryptoManager.deriveMasterKey(phrase)
        secureStorage.putBytes(KEY_IDENTITY_KEY, identityKey)
        Napier.d("Identity recovered from recovery phrase")
    }

    /**
     * Retrieves the identity key for encryption/decryption operations.
     *
     * @throws IdentityKeyNotFoundException if no identity has been set up
     */
    suspend fun getIdentityKey(): ByteArray {
        val key = secureStorage.getBytes(KEY_IDENTITY_KEY)
        if (key == null) {
            Napier.w("Identity key not found - user must set up or recover")
            throw IdentityKeyNotFoundException(
                "No identity key found. User must complete onboarding or recovery.",
            )
        }
        return key
    }

    /**
     * Clears the identity key from the device.
     *
     * Used when user explicitly signs out or unlinks device.
     */
    suspend fun clearIdentityKey() {
        secureStorage.remove(KEY_IDENTITY_KEY)
        Napier.d("Identity key cleared from device")
    }

    companion object {
        private const val KEY_IDENTITY_KEY = "identity_key_v1"
    }
}

/**
 * Represents a 12-word BIP-39 recovery phrase.
 *
 * These words should be written down and stored safely by the user.
 */
@Serializable
data class RecoveryPhrase(
    val words: List<String>,
) {
    init {
        require(words.size == 12) { "Recovery phrase must be exactly 12 words" }
    }

    /**
     * Returns the phrase as a space-separated string for display/entry.
     */
    override fun toString(): String = words.joinToString(" ")
}

class IdentityKeyNotFoundException(
    message: String,
) : Exception(message)
