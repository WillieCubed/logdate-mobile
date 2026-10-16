package app.logdate.client.device.crypto

import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IdentityBackupContinuityTest {
    private val storage = InMemorySecureStorage()
    private val crypto = FakeCryptoManager()
    private val backup = InMemoryIdentityKeyBackupStore()
    private val manager = IdentityKeyManager(storage, crypto, backup)

    @Test
    fun `startup backfill cannot bind the old cached media key to a recovered identity`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var replacing = false
            val gated =
                object : IdentityKeyBackupStore by backup {
                    override suspend fun writePhrase(phrase: String) {
                        backup.writePhrase(phrase)
                        if (replacing) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                }
            val identity = IdentityKeyManager(storage, crypto, gated)
            identity.setupNewIdentity()
            storage.putBytes("media_payload_key_v1", ByteArray(32) { 8 })
            identity.ensureExistingIdentityBackup()
            val words = List(12) { "recovered-$it" }
            val binding = Base64.encode(crypto.hmacSha256(crypto.deriveMasterKey(words), "native-media-key-backup-v1".encodeToByteArray()))
            replacing = true
            val recovery = launch { identity.recoverIdentity(words) }
            entered.await()
            val backfill = launch { identity.ensureExistingIdentityBackup() }
            runCurrent()
            release.complete(Unit)
            recovery.join()
            backfill.join()
            assertEquals(null, backup.readMediaIdentity(binding))
        }

    @Test
    fun `direct provisioning cannot mint over orphaned media recovery material`() =
        runTest {
            backup.writeMediaIdentity(MediaIdentityBackup("retained-binding", ByteArray(32) { 8 }))
            assertFailsWith<IllegalStateException> { manager.setupNewIdentity() }
            assertFalse(manager.hasIdentityKey())
        }

    @Test
    fun `orphaned media recovery material cannot mint a replacement identity`() =
        runTest {
            backup.writeMediaIdentity(MediaIdentityBackup("retained-binding", ByteArray(32) { 8 }))
            assertFailsWith<IllegalStateException> { manager.ensureIdentityKey() }
            assertFalse(manager.hasIdentityKey())
            assertTrue(ByteArray(32) { 8 }.contentEquals(backup.readMediaIdentity("retained-binding")?.key))
        }

    @Test
    fun `offline upgrade preserves an independent media key across native restore`() =
        runTest {
            val root = ByteArray(32) { 7 }
            val legacyMedia = ByteArray(32) { 8 }
            storage.putBytes("identity_key_v1", root)
            storage.putString("identity_account_id_v1", "account-a")
            storage.putBytes("media_payload_key_v1", legacyMedia)
            manager.ensureExistingIdentityBackup()
            val destinationStorage = InMemorySecureStorage()
            val destination = IdentityKeyManager(destinationStorage, crypto, backup)
            destination.ensureIdentityKey()
            assertTrue(root.contentEquals(destination.getIdentityKey()))
            assertTrue(legacyMedia.contentEquals(destinationStorage.getBytes("media_payload_key_v1")))
        }

    @Test
    fun `existing-install backup preparation never creates a new identity`() =
        runTest {
            manager.ensureExistingIdentityBackup()
            assertFalse(manager.hasIdentityKey())
            assertFalse(backup.hasBackup())
        }

    @Test
    fun `backup preparation preserves conflicting media recovery keys`() =
        runTest {
            manager.installAccountKey("account-a", ByteArray(32) { 7 })
            storage.putBytes("media_payload_key_v1", ByteArray(32) { 8 })
            manager.ensureExistingIdentityBackup()
            storage.putBytes("media_payload_key_v1", ByteArray(32) { 9 })
            assertFailsWith<IllegalStateException> { manager.ensureExistingIdentityBackup() }
            assertTrue(
                ByteArray(32) {
                    8
                }.contentEquals(
                    backup
                        .readMediaIdentity(
                            Base64.encode(
                                crypto.hmacSha256(
                                    ByteArray(32) {
                                        7
                                    },
                                    "native-media-key-backup-v1".encodeToByteArray(),
                                ),
                            ),
                        )?.key,
                ),
            )
        }

    @Test
    fun `phrase-only store replacement contract removes the obsolete phrase`() =
        runTest {
            var phrase: String? = List(12) { "stale-$it" }.joinToString(" ")
            val phraseOnly =
                object : IdentityKeyBackupStore {
                    override suspend fun readPhrase() = phrase

                    override suspend fun writePhrase(phrase: String) = Unit

                    override suspend fun clear() {
                        phrase = null
                    }
                }
            phraseOnly.writeAccountIdentity(AccountIdentityBackup("account-a", ByteArray(32) { 7 }))
            assertEquals(null, phraseOnly.readPhrase())
            assertFalse(IdentityKeyManager(InMemorySecureStorage(), crypto, phraseOnly).restoreFromBackupIfAvailable())
        }

    @Test
    fun `primary account key cannot overwrite a conflicting retained key`() =
        runTest {
            val original = ByteArray(32) { 7 }
            storage.putBytes("identity_key_v1", original)
            storage.putString("identity_account_id_v1", "account-a")
            val conflicting = ByteArray(32) { 8 }
            backup.writeAccountIdentity(AccountIdentityBackup("account-a", conflicting))
            assertFailsWith<IllegalStateException> { manager.ensureIdentityKey() }
            assertTrue(conflicting.contentEquals(backup.readAccountIdentity()?.key))
            assertTrue(original.contentEquals(manager.getIdentityKey()))
        }

    @Test
    fun `primary account key cannot overwrite a retained key belonging to another owner`() =
        runTest {
            val original = ByteArray(32) { 7 }
            storage.putBytes("identity_key_v1", original)
            storage.putString("identity_account_id_v1", "account-a")
            backup.writeAccountIdentity(AccountIdentityBackup("account-b", original))
            assertFailsWith<IllegalStateException> { manager.ensureIdentityKey() }
            assertEquals("account-b", backup.readAccountIdentity()?.accountId)
        }

    @Test
    fun `primary phrase cannot overwrite malformed retained recovery material`() =
        runTest {
            val phrase = manager.setupNewIdentity()
            backup.writePhrase("truncated")
            assertFailsWith<IllegalStateException> { manager.ensureIdentityKey() }
            assertEquals("truncated", backup.readPhrase())
            assertEquals(phrase, manager.getStoredRecoveryPhrase())
        }

    @Test
    fun `primary phrase cannot overwrite a different valid retained phrase`() =
        runTest {
            val phrase = manager.setupNewIdentity()
            val other = List(12) { "other-$it" }.joinToString(" ")
            backup.writePhrase(other)
            assertFailsWith<IllegalStateException> { manager.ensureIdentityKey() }
            assertEquals(other, backup.readPhrase())
            assertEquals(phrase, manager.getStoredRecoveryPhrase())
        }
}
