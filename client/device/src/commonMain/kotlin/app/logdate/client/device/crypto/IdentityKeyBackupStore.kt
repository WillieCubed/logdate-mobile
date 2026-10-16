package app.logdate.client.device.crypto

/**
 * A secondary copy of the identity recovery phrase, kept outside [app.logdate.client.device.storage.SecureStorage]'s
 * KeyStore/Keychain binding so it can survive what that binding cannot: a reinstall, a
 * device-to-device transfer, or a cloud restore onto a new phone that leaves the platform
 * keystore empty even though the account already has data in the cloud.
 *
 * Phrase-derived identities retain their recovery phrase. Implementations supporting
 * phrase-less account identities also retain an account-bound key through the same
 * protected native transport. Writing either representation replaces the other.
 *
 * Implementations trade the hardware-backed defense-in-depth of [app.logdate.client.device.storage.SecureStorage]
 * for durability across exactly the transfers that break it: an Android file mirrored into the
 * OS backup/device-transfer mechanism, or an iOS Keychain item marked to sync via iCloud
 * Keychain. That trade-off is deliberate -- losing hardware backing is preferable to
 * irrecoverably orphaning a user's already-synced data.
 */
interface IdentityKeyBackupStore {
    suspend fun readAccountIdentity(): AccountIdentityBackup? = null

    suspend fun writeAccountIdentity(identity: AccountIdentityBackup) = clear()

    suspend fun readMediaIdentity(identityBinding: String): MediaIdentityBackup? = null

    suspend fun hasMediaBackup(): Boolean = false

    suspend fun writeMediaIdentity(identity: MediaIdentityBackup) = Unit

    suspend fun hasBackup(): Boolean = readPhrase() != null || readAccountIdentity() != null || hasMediaBackup()

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

class AccountIdentityBackup(
    val accountId: String,
    val key: ByteArray,
    val mediaKey: ByteArray? = null,
)

class MediaIdentityBackup(
    val identityBinding: String,
    val key: ByteArray,
)

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
