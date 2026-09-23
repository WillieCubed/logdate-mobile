package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage

/**
 * Whether this device needs its recovery phrase entered before sync can safely proceed.
 *
 * Set when sync finds no identity key locally but the account already has data in the cloud --
 * minting a new key in that situation would encrypt everything from now on under a key that can
 * never read what came before. Persisted (not just held in memory) so the paused state survives
 * the process restarts Android does routinely in the background, and so it is not silently
 * re-decided -- and possibly gotten wrong -- on every single sync attempt.
 */
interface IdentityRecoveryNeededStore {
    suspend fun isNeeded(): Boolean

    suspend fun setNeeded(needed: Boolean)
}

class InMemoryIdentityRecoveryNeededStore : IdentityRecoveryNeededStore {
    private var needed = false

    override suspend fun isNeeded(): Boolean = needed

    override suspend fun setNeeded(needed: Boolean) {
        this.needed = needed
    }
}

class KeyValueIdentityRecoveryNeededStore(
    private val storage: KeyValueStorage,
) : IdentityRecoveryNeededStore {
    override suspend fun isNeeded(): Boolean = storage.getBoolean(KEY, false)

    override suspend fun setNeeded(needed: Boolean) {
        storage.putBoolean(KEY, needed)
    }

    private companion object {
        const val KEY = "identity_recovery_needed"
    }
}
