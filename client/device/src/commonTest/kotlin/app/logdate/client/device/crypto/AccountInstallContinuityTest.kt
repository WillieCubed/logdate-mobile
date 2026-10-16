package app.logdate.client.device.crypto

import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AccountInstallContinuityTest {
    private val storage = InMemorySecureStorage()
    private val crypto = FakeCryptoManager()
    private val backup = InMemoryIdentityKeyBackupStore()
    private val manager = IdentityKeyManager(storage, crypto, backup)

    @Test
    fun `direct phrase recovery preserves media from a transferred account pair`() =
        runTest {
            val phrase = crypto.generateRecoveryPhrase()
            val root = crypto.deriveMasterKey(phrase)
            val media = ByteArray(32) { 9 }
            backup.writeAccountIdentity(AccountIdentityBackup("account-a", root, media))
            manager.recoverIdentity(phrase)
            manager.ensureExistingIdentityBackup()
            assertContentEquals(media, storage.getBytes("media_payload_key_v1"))
        }

    @Test
    fun `switching phrase preserves the transferred pair for later recovery`() =
        runTest {
            val original = crypto.generateRecoveryPhrase()
            val root = crypto.deriveMasterKey(original)
            val media = ByteArray(32) { 9 }
            backup.writeAccountIdentity(AccountIdentityBackup("account-a", root, media))
            manager.recoverIdentity(crypto.generateRecoveryPhrase())
            manager.recoverIdentity(original)
            manager.ensureExistingIdentityBackup()
            assertContentEquals(media, storage.getBytes("media_payload_key_v1"))
        }

    @Test
    fun `interrupted account install retains a recoverable root and independent media pair`() =
        runTest {
            var failMediaWrite = true
            val failing =
                object : app.logdate.client.device.storage.SecureStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (key == "media_payload_key_v1" && failMediaWrite) {
                            failMediaWrite = false
                            throw IllegalStateException("Interrupted media persistence")
                        }
                        storage.putString(key, value)
                    }
                }
            val root = ByteArray(32) { 7 }
            val media = ByteArray(32) { 8 }
            val source = IdentityKeyManager(failing, crypto, backup)
            assertFailsWith<IllegalStateException> { source.installAccountKey("account-a", root, media) }
            val destinationStorage = InMemorySecureStorage()
            val destination = IdentityKeyManager(destinationStorage, crypto, backup)
            destination.ensureIdentityKey()
            assertContentEquals(root, destination.getIdentityKey())
            assertContentEquals(media, destinationStorage.getBytes("media_payload_key_v1"))
            source.ensureExistingIdentityBackup()
            assertContentEquals(media, storage.getBytes("media_payload_key_v1"))
        }

    @Test
    fun `account unlock cannot overwrite a different retained root`() =
        runTest {
            val retained = ByteArray(32) { 7 }
            backup.writeAccountIdentity(AccountIdentityBackup("account-a", retained))
            assertFailsWith<IllegalStateException> { manager.installAccountKey("account-a", ByteArray(32) { 8 }) }
            assertContentEquals(retained, backup.readAccountIdentity()?.key)
            assertFalse(manager.hasIdentityKey())
        }

    @Test
    fun `account unlock cannot replace a retained phrase with a different root`() =
        runTest {
            val phrase = List(12) { "retained-$it" }.joinToString(" ")
            backup.writePhrase(phrase)
            assertFailsWith<IllegalStateException> { manager.installAccountKey("account-a", ByteArray(32) { 8 }) }
            assertEquals(phrase, backup.readPhrase())
            assertFalse(manager.hasIdentityKey())
        }

    @Test
    fun `account unlock cannot install a root for a different retained journal owner`() =
        runTest {
            val identity = IdentityKeyManager(storage, crypto, backup, expectedAccountOwner = { "account-a" })
            assertFailsWith<IllegalArgumentException> { identity.installAccountKey("account-b", ByteArray(32) { 8 }) }
            assertFalse(identity.hasIdentityKey())
            assertFalse(backup.hasBackup())
        }

    @Test
    fun `recovering the same phrase preserves an unmirrored legacy media key`() =
        runTest {
            val phrase = manager.setupNewIdentity()
            val media = ByteArray(32) { 8 }
            storage.putBytes("media_payload_key_v1", media)
            manager.recoverIdentity(phrase.words)
            assertContentEquals(media, storage.getBytes("media_payload_key_v1"))
        }

    @Test
    fun `new identity cannot replace media-only primary recovery material`() =
        runTest {
            storage.putBytes("media_payload_key_v1", ByteArray(32) { 8 })
            assertFailsWith<IllegalStateException> { manager.setupNewIdentity() }
            assertFalse(manager.hasIdentityKey())
        }
}
