package app.logdate.client.device.crypto

import app.logdate.client.device.identity.sameOwnerId
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import kotlin.io.encoding.Base64

/** Called under IdentityKeyManager's lock; validates retained copies before any backfill. */
internal class IdentityBackupCoordinator(
    private val storage: SecureStorage,
    private val crypto: CryptoManager,
    private val backup: IdentityKeyBackupStore,
) {
    fun binding(key: ByteArray): String = Base64.encode(crypto.hmacSha256(key, "native-media-key-backup-v1".encodeToByteArray()))

    suspend fun validateRoot(
        key: ByteArray,
        accountId: String?,
    ): Pair<AccountIdentityBackup?, String?> {
        check(key.size == 32) { "Invalid primary identity key" }
        val account = backup.readAccountIdentity()
        val phrase = backup.readPhrase()
        if (account != null) {
            check(
                sameOwnerId(account.accountId, accountId) && account.key.contentEquals(key),
            ) { "The retained account identity conflicts with the key" }
        }
        if (phrase != null) validatePhrase(phrase, key)
        if (account == null && phrase == null) {
            check(!backup.hasRootBackup()) { "The retained identity backup needs recovery" }
        }
        return account to phrase
    }

    suspend fun validateMedia(
        key: ByteArray,
        media: ByteArray?,
        account: AccountIdentityBackup?,
    ): ByteArray? {
        val retained = backup.readMediaIdentity(binding(key))?.key
        val paired = account?.mediaKey
        check(media == null || media.size == 32) { "Invalid primary media key" }
        check(paired == null || paired.size == 32) { "Invalid paired media key" }
        check(retained == null || retained.size == 32) { "Invalid retained media key" }
        check(paired == null || retained == null || paired.contentEquals(retained)) { "Conflicting portable media recovery keys" }
        val selected = paired ?: retained
        check(
            media == null || selected == null || media.contentEquals(selected),
        ) { "The retained media key conflicts with the primary media key" }
        return selected
    }

    suspend fun preserveMedia(
        key: ByteArray,
        media: ByteArray,
    ) {
        check(key.size == 32 && media.size == 32) { "Invalid outgoing encryption keys" }
        val account = backup.readAccountIdentity()
        check(account == null || !account.key.contentEquals(key) || account.mediaKey == null || account.mediaKey.contentEquals(media)) {
            "Conflicting paired media recovery key"
        }
        val retained = backup.readMediaIdentity(binding(key))
        check(retained == null || retained.key.contentEquals(media)) { "Conflicting outgoing media recovery keys" }
        if (retained == null) backup.writeMediaIdentity(MediaIdentityBackup(binding(key), media))
    }

    suspend fun synchronize(
        key: ByteArray,
        phrase: String?,
        accountId: String?,
    ) {
        val (account, retainedPhrase) = validateRoot(key, accountId)
        if (phrase != null) validatePhrase(phrase, key)
        val media = storage.getBytes(MEDIA_KEY)
        val selected = validateMedia(key, media, account)
        if (account == null && retainedPhrase == null) {
            if (phrase != null) {
                backup.writePhrase(phrase)
            } else if (accountId != null) {
                backup.writeAccountIdentity(AccountIdentityBackup(accountId, key, media))
            }
        }
        if (media != null && selected == null) {
            preserveMedia(key, media)
        } else if (media == null && selected != null) {
            storage.putBytes(MEDIA_KEY, selected)
        }
    }

    suspend fun validatePhrase(
        phrase: String,
        key: ByteArray,
    ) {
        val words = phrase.trim().split(Regex("\\s+"))
        check(crypto.validateRecoveryPhrase(words) && crypto.deriveMasterKey(words).contentEquals(key)) {
            "The recovery phrase conflicts with its identity key"
        }
    }

    private companion object {
        const val MEDIA_KEY = "media_payload_key_v1"
    }
}
