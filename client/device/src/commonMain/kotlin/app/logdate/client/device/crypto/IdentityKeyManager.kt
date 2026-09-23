package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val backupStore: IdentityKeyBackupStore = NoOpIdentityKeyBackupStore,
) {
    /**
     * Checks if this device has already been set up with an identity key.
     */
    suspend fun hasIdentityKey(): Boolean = secureStorage.getBytes(KEY_IDENTITY_KEY) != null

    private val identityMutex = Mutex()

    /**
     * Guarantees this device has an identity key, creating one if it does not.
     *
     * Sync encrypts every note, journal, and draft with a key derived from this one, so the key
     * is infrastructure rather than a user-facing choice -- it must exist before the first upload
     * regardless of whether the user has been shown their recovery phrase yet. Showing the phrase
     * is a separate concern, handled from settings.
     *
     * If [SecureStorage] has no key but [backupStore] holds a recoverable phrase -- the usual
     * shape of a reinstall or a restore onto a new device -- that phrase is used to silently
     * restore the key rather than minting a new, unrelated identity. Callers that need to tell
     * "restored" apart from "already had a key" (to reset sync state that depends on the
     * identity, for example) should use [restoreFromBackupIfAvailable] directly instead.
     *
     * Idempotent, and safe to call concurrently: callers racing here would otherwise each derive a
     * different key and the later write would orphan content encrypted under the earlier one.
     */
    suspend fun ensureIdentityKey() {
        identityMutex.withLock {
            if (hasIdentityKey()) return@withLock
            if (restoreFromBackupLocked()) return@withLock
            setupNewIdentity()
        }
    }

    /**
     * Sets up a new identity for the first time.
     *
     * This generates a new recovery phrase and derives the identity key from it.
     * The recovery phrase is stored in [SecureStorage] so the user can reference it later from
     * app settings without sending it to LogDate servers or regular app preferences. It is also
     * mirrored into [backupStore] so it survives a device transfer or cloud restore that
     * [SecureStorage]'s KeyStore/Keychain binding does not.
     *
     * @return The recovery phrase the user must store safely
     */
    suspend fun setupNewIdentity(): RecoveryPhrase {
        require(!hasIdentityKey()) { "Identity key already exists" }

        val phrase = cryptoManager.generateRecoveryPhrase()
        val identityKey = cryptoManager.deriveMasterKey(phrase)
        val phraseText = phrase.joinToString(" ")

        secureStorage.putBytes(KEY_IDENTITY_KEY, identityKey)
        secureStorage.putString(KEY_RECOVERY_PHRASE, phraseText)
        backupStore.writePhrase(phraseText)
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
        val phraseText = phrase.joinToString(" ")
        secureStorage.putBytes(KEY_IDENTITY_KEY, identityKey)
        secureStorage.putString(KEY_RECOVERY_PHRASE, phraseText)
        backupStore.writePhrase(phraseText)
        Napier.d("Identity recovered from recovery phrase")
    }

    /**
     * Attempts to silently restore the identity key from [backupStore] when [SecureStorage] has
     * none -- the same outcome as a successful [recoverIdentity] call, but driven by a phrase this
     * device already had a backup copy of rather than one the user re-typed.
     *
     * Returns `true` only when a key was actually restored. Returns `false` both when a key
     * already exists (nothing to do) and when there was nothing usable to restore, so callers can
     * tell "just restored" apart from "already had a key" and react accordingly -- e.g. resetting
     * download cursors and cached keys that were derived from a since-replaced identity.
     */
    suspend fun restoreFromBackupIfAvailable(): Boolean = identityMutex.withLock { restoreFromBackupLocked() }

    /** Must only be called while holding [identityMutex]. */
    private suspend fun restoreFromBackupLocked(): Boolean {
        if (hasIdentityKey()) return false

        val phraseText = backupStore.readPhrase() ?: return false
        val words =
            phraseText
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
        if (words.size != RECOVERY_PHRASE_WORD_COUNT || !cryptoManager.validateRecoveryPhrase(words)) {
            Napier.w("Identity key backup held an unusable recovery phrase; ignoring it")
            return false
        }

        val identityKey = cryptoManager.deriveMasterKey(words)
        secureStorage.putBytes(KEY_IDENTITY_KEY, identityKey)
        secureStorage.putString(KEY_RECOVERY_PHRASE, phraseText)
        Napier.i("Identity key silently restored from backup store")
        return true
    }

    /**
     * Retrieves the user's recovery phrase from local secure storage.
     *
     * This is intentionally local-only. The server never receives the phrase and cannot use this
     * method to decrypt user content.
     */
    suspend fun getStoredRecoveryPhrase(): RecoveryPhrase? {
        val phrase =
            secureStorage
                .getString(KEY_RECOVERY_PHRASE)
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                ?: return null
        if (phrase.size != RECOVERY_PHRASE_WORD_COUNT) {
            Napier.w("Stored recovery phrase is malformed")
            return null
        }
        return RecoveryPhrase(phrase)
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
        secureStorage.remove(KEY_RECOVERY_PHRASE)
        backupStore.clear()
        Napier.d("Identity key cleared from device")
    }

    companion object {
        private const val KEY_IDENTITY_KEY = "identity_key_v1"
        private const val KEY_RECOVERY_PHRASE = "identity_recovery_phrase_v1"
        private const val RECOVERY_PHRASE_WORD_COUNT = 12
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
