package app.logdate.client.device.crypto

import app.logdate.client.device.storage.putBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdentityAccountCasingTest {
    private val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
    private val root = ByteArray(32) { (it + 4).toByte() }
    private val media = ByteArray(32) { (it + 44).toByte() }

    @Test
    fun `same account can use and bind existing keys without changing raw ownership`() =
        runTest {
            val storage = InMemorySecureStorage()
            storage.putString("identity_account_id_v1", raw)
            storage.putBytes("identity_key_v1", root)
            val manager = IdentityKeyManager(storage, FakeCryptoManager(), expectedAccountOwner = { raw })
            assertTrue(manager.canUseForAccount(raw.lowercase()))
            assertTrue(manager.bindToAccount(raw.lowercase()))
            assertEquals(raw, storage.getString("identity_account_id_v1"))
            assertTrue(manager.getIdentityKey().contentEquals(root))
        }

    @Test
    fun `account unlock preserves both retained owner spellings and original keys`() =
        runTest {
            val storage = InMemorySecureStorage()
            val backup = InMemoryIdentityKeyBackupStore()
            storage.putString("identity_account_id_v1", raw)
            storage.putBytes("identity_key_v1", root)
            storage.putBytes("media_payload_key_v1", media)
            backup.writeAccountIdentity(AccountIdentityBackup(raw, root.copyOf(), media.copyOf()))
            val manager = IdentityKeyManager(storage, FakeCryptoManager(), backup, expectedAccountOwner = { raw })
            manager.installAccountKey(raw.lowercase(), root.copyOf(), media.copyOf())
            val snapshot = manager.snapshotAccountKeys(raw.lowercase()) { media.copyOf() }
            assertTrue(snapshot.key.contentEquals(root))
            assertTrue(snapshot.mediaKey!!.contentEquals(media))
            assertEquals(raw, storage.getString("identity_account_id_v1"))
            assertEquals(raw, backup.readAccountIdentity()!!.accountId)
            assertFailsWith<IllegalArgumentException> {
                manager.installAccountKey(raw.lowercase(), ByteArray(32) { 99 }, media.copyOf())
            }
            assertTrue(manager.getIdentityKey().contentEquals(root))
        }

    @Test
    fun `backup recovery accepts equivalent owner while preserving existing secure spelling`() =
        runTest {
            val storage = InMemorySecureStorage()
            val backup = InMemoryIdentityKeyBackupStore()
            storage.putString("identity_account_id_v1", raw.lowercase())
            backup.writeAccountIdentity(AccountIdentityBackup(raw, root.copyOf(), media.copyOf()))
            val manager = IdentityKeyManager(storage, FakeCryptoManager(), backup, expectedAccountOwner = { raw })
            assertTrue(manager.restoreFromBackupIfAvailable())
            assertEquals(raw.lowercase(), storage.getString("identity_account_id_v1"))
            assertEquals(raw, backup.readAccountIdentity()!!.accountId)
            assertTrue(manager.getIdentityKey().contentEquals(root))
        }

    @Test
    fun `read-only snapshot recognizes account casing without rewriting retained state`() =
        runTest {
            val storage = InMemorySecureStorage()
            val backup = InMemoryIdentityKeyBackupStore()
            storage.putString("identity_account_id_v1", raw)
            storage.putBytes("identity_key_v1", root)
            storage.putBytes("media_payload_key_v1", media)
            backup.writeAccountIdentity(AccountIdentityBackup(raw, root.copyOf(), media.copyOf()))
            val manager = IdentityKeyManager(storage, FakeCryptoManager(), backup, expectedAccountOwner = { raw })
            val snapshot = assertNotNull(manager.snapshotExistingKeys(raw.lowercase()) { error("Existing media must win") })
            assertEquals(raw, snapshot.accountId)
            assertEquals(raw, storage.getString("identity_account_id_v1"))
            assertEquals(raw, backup.readAccountIdentity()!!.accountId)
        }
}
