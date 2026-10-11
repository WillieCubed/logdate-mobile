package app.logdate.server.identity

import java.security.GeneralSecurityException

/** One stored private key, encrypted under a key-encryption key. */
data class EncryptedPrivateKey(
    val id: String,
    val privateKeyEncrypted: String,
)

/** A table of private keys encrypted at rest, as [StoredKeyRewrap] sees it. */
interface EncryptedPrivateKeyStore {
    suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey>

    /**
     * Replaces the encrypted value of key [id] with [replacement], but only while it still equals
     * [expected], so instances racing to re-encrypt the same key can't overwrite each other.
     * Returns whether the value was replaced.
     */
    suspend fun replaceEncryptedPrivateKey(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean
}

/**
 * Re-encrypts every stored private key under the current key-encryption key, so the previous one
 * can be retired.
 *
 * It's safe to run on every instance at once and to run repeatedly: keys the current
 * key-encryption key already encrypted are left alone, and each replacement only applies if no
 * other instance changed the key first. A key no known key-encryption key can read is reported and
 * never overwritten.
 */
class StoredKeyRewrap(
    private val stores: Map<String, EncryptedPrivateKeyStore>,
    private val cipher: AtRestKeyCipher,
) {
    suspend fun run(): Report {
        var rewrapped = 0
        var alreadyCurrent = 0
        val unreadable = mutableListOf<String>()
        for ((storeName, store) in stores) {
            for (key in store.encryptedPrivateKeys()) {
                val replacement =
                    try {
                        cipher.rewrap(key.privateKeyEncrypted)
                    } catch (_: GeneralSecurityException) {
                        unreadable += "$storeName/${key.id}"
                        continue
                    }
                when {
                    replacement == null -> alreadyCurrent++
                    store.replaceEncryptedPrivateKey(key.id, key.privateKeyEncrypted, replacement) -> rewrapped++
                    else -> alreadyCurrent++
                }
            }
        }
        return Report(rewrapped = rewrapped, alreadyCurrent = alreadyCurrent, unreadable = unreadable)
    }

    data class Report(
        val rewrapped: Int,
        val alreadyCurrent: Int,
        val unreadable: List<String>,
    )
}
