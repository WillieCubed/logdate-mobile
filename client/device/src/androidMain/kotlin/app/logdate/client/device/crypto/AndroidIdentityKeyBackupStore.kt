package app.logdate.client.device.crypto

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Stores a recoverable backup of the identity recovery phrase in a file within
 * [Context.getFilesDir]. Mirrors [app.logdate.client.database.encryption.PassphraseBackupStore]'s
 * trade-off for the database passphrase: [app.logdate.client.device.storage.SecureStorage]
 * (EncryptedSharedPreferences backed by the Android KeyStore) is lost whenever the device's
 * KeyStore doesn't survive a device-to-device transfer or cloud restore, while this file does --
 * it is included in both, via `data_extraction_rules.xml` and `backup_rules.xml`.
 *
 * Phrase-derived identities store their phrase. Account identities installed without a
 * phrase store a versioned account-bound key instead. Both representations require the
 * encrypted cloud transport configured by the app's native backup rules.
 *
 * ## Security model
 *
 * Protected the same way as the database passphrase backup:
 * - Linux file permissions (readable only by the app process)
 * - Android file-based encryption (FBE) at rest
 * - Google's client-side encryption in cloud backups
 *
 * It is NOT protected by a hardware-backed KeyStore key, which is a deliberate trade-off: losing
 * the hardware-backed defense-in-depth is preferable to irrecoverable data loss when the KeyStore
 * doesn't survive a device transfer.
 */
class AndroidIdentityKeyBackupStore(
    private val context: Context,
) : IdentityKeyBackupStore {
    override suspend fun hasMediaBackup(): Boolean = mediaFile().exists()

    override suspend fun readMediaIdentity(identityBinding: String): MediaIdentityBackup? =
        readMediaIdentities()
            .firstOrNull { it.identityBinding == identityBinding }
            ?.let { MediaIdentityBackup(it.identityBinding, Base64.decode(it.key)) }

    private fun readMediaIdentities(): List<StoredMediaIdentity> {
        val file = mediaFile()
        if (!file.exists()) return emptyList()
        return try {
            check(file.length() in 1..32768) { "Invalid media recovery backup size" }
            val stored = Json.decodeFromString<StoredMediaIdentities>(file.readText())
            check(stored.version == 1 && stored.identities.size in 1..64) { "Unsupported media recovery backup" }
            check(
                stored.identities
                    .map { it.identityBinding }
                    .distinct()
                    .size == stored.identities.size,
            ) {
                "Duplicate media recovery bindings"
            }
            stored.identities.forEach {
                val key = Base64.decode(it.key)
                check(Base64.decode(it.identityBinding).size == 32 && key.size == 32) { "Invalid media recovery key" }
                check(MessageDigest.isEqual(Base64.decode(it.checksum), checksum(it.identityBinding, key))) {
                    "Media recovery backup integrity check failed"
                }
            }
            stored.identities
        } catch (error: Exception) {
            throw IllegalStateException("The retained media recovery backup could not be read", error)
        }
    }

    override suspend fun writeMediaIdentity(identity: MediaIdentityBackup) {
        require(identity.key.size == 32 && Base64.decode(identity.identityBinding).size == 32)
        val retained = readMediaIdentities()
        val existing = retained.firstOrNull { it.identityBinding == identity.identityBinding }
        if (existing != null) {
            check(Base64.decode(existing.key).contentEquals(identity.key)) { "Conflicting media recovery keys" }
            return
        }
        check(retained.size < 64) { "The retained media recovery keys need attention" }
        val stored =
            StoredMediaIdentity(
                identityBinding = identity.identityBinding,
                key = Base64.encode(identity.key),
                checksum = Base64.encode(checksum(identity.identityBinding, identity.key)),
            )
        writeAtomically(mediaFile(), Json.encodeToString(StoredMediaIdentities(1, retained + stored)))
    }

    override suspend fun hasBackup(): Boolean = backupFile().exists() || accountFile().exists() || mediaFile().exists()

    override suspend fun readAccountIdentity(): AccountIdentityBackup? {
        val file = accountFile()
        if (!file.exists()) return null
        return try {
            check(file.length() in 1..4096) { "Invalid account identity backup size" }
            val stored = Json.decodeFromString<StoredAccountIdentity>(file.readText())
            check(stored.version in 1..2 && stored.accountId.isNotBlank()) { "Unsupported account identity backup" }
            val key = Base64.decode(stored.key)
            check(key.size == 32) { "Invalid account identity key length" }
            val media = stored.mediaKey?.let { Base64.decode(it) }
            check(
                (stored.version == 1 && media == null) || (stored.version == 2 && media?.size == 32),
            ) { "Invalid paired media recovery key" }
            check(MessageDigest.isEqual(Base64.decode(stored.checksum), checksum(stored.accountId, key + (media ?: byteArrayOf())))) {
                "Account identity backup integrity check failed"
            }
            AccountIdentityBackup(stored.accountId, key, media)
        } catch (error: Exception) {
            Napier.w("Native account identity backup needs recovery")
            throw IllegalStateException("The retained account identity backup could not be read", error)
        }
    }

    override suspend fun writeAccountIdentity(identity: AccountIdentityBackup) {
        require(identity.key.size == 32 && identity.accountId.isNotBlank() && (identity.mediaKey == null || identity.mediaKey.size == 32))
        val stored =
            StoredAccountIdentity(
                if (identity.mediaKey == null) 1 else 2,
                identity.accountId,
                Base64.encode(identity.key),
                Base64.encode(checksum(identity.accountId, identity.key + (identity.mediaKey ?: byteArrayOf()))),
                identity.mediaKey?.let { Base64.encode(it) },
            )
        writeAtomically(accountFile(), Json.encodeToString(stored))
        deleteIfPresent(backupFile())
    }

    override suspend fun readPhrase(): String? =
        runCatching {
            val file = backupFile()
            if (!file.exists()) return null
            val phrase = file.readText().trim()
            phrase.ifEmpty { null }
        }.getOrElse { error ->
            Napier.w("Failed to read identity recovery phrase backup", error)
            null
        }

    override suspend fun writePhrase(phrase: String) {
        writeAtomically(backupFile(), phrase)
        deleteIfPresent(accountFile())
    }

    override suspend fun clear() {
        deleteIfPresent(backupFile())
        deleteIfPresent(accountFile())
        deleteIfPresent(mediaFile())
    }

    private fun backupFile(): File = File(context.filesDir, BACKUP_PATH)

    private fun mediaFile(): File = File(context.filesDir, "identity_recovery/media_key_backup")

    private fun accountFile(): File = File(context.filesDir, "identity_recovery/account_key_backup")

    private fun checksum(
        accountId: String,
        key: ByteArray,
    ): ByteArray = MessageDigest.getInstance("SHA-256").digest(accountId.encodeToByteArray() + key)

    private fun writeAtomically(
        file: File,
        contents: String,
    ) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use {
            it.write(contents.encodeToByteArray())
            it.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun deleteIfPresent(file: File) {
        check(!file.exists() || file.delete()) { "Unable to remove obsolete identity recovery material" }
    }

    private companion object {
        const val BACKUP_PATH = "identity_recovery/phrase_backup"
    }
}

@Serializable
private data class StoredAccountIdentity(
    val version: Int,
    val accountId: String,
    val key: String,
    val checksum: String,
    val mediaKey: String? = null,
)

@Serializable
private data class StoredMediaIdentity(
    val identityBinding: String,
    val key: String,
    val checksum: String,
)

@Serializable
private data class StoredMediaIdentities(
    val version: Int,
    val identities: List<StoredMediaIdentity>,
)
