package app.logdate.client.device.crypto

/**
 * A secondary copy of the identity recovery phrase, kept outside [app.logdate.client.device.storage.SecureStorage]'s
 * KeyStore/Keychain binding so it can survive what that binding cannot: a reinstall, a
 * device-to-device transfer, or a cloud restore onto a new phone that leaves the platform
 * keystore empty even though the account already has data in the cloud.
 *
 * Only the recovery phrase is kept here, never the derived identity key itself -- the key is
 * deterministically re-derived from the phrase (see [IdentityKeyManager.setupNewIdentity]), so
 * backing up the phrase is enough to restore the key on a new device.
 *
 * Implementations trade the hardware-backed defense-in-depth of [app.logdate.client.device.storage.SecureStorage]
 * for durability across exactly the transfers that break it: an Android file mirrored into the
 * OS backup/device-transfer mechanism, or an iOS Keychain item marked to sync via iCloud
 * Keychain. That trade-off is deliberate -- losing hardware backing is preferable to
 * irrecoverably orphaning a user's already-synced data.
 */
interface IdentityKeyBackupStore {
    /**
     * Reads the backed-up recovery phrase, or null if no backup exists or it's unreadable.
     */
    suspend fun readPhrase(): String?

    /**
     * Writes the recovery phrase to the backup.
     */
    suspend fun writePhrase(phrase: String)

    /**
     * Deletes the backup.
     */
    suspend fun clear()
}

/**
 * A backup store that never has anything to restore and silently discards writes.
 *
 * Used on platforms with no cloud backup/transfer mechanism to mirror into (desktop today), and
 * as the default for tests that don't exercise backup/restore behavior.
 */
object NoOpIdentityKeyBackupStore : IdentityKeyBackupStore {
    override suspend fun readPhrase(): String? = null

    override suspend fun writePhrase(phrase: String) {}

    override suspend fun clear() {}
}
