package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RootlessIdentityKeyObservationTest {
    @Test
    fun `empty vault observation does not create an encryption identity`() =
        runTest {
            val f = Fixture()
            val observed = assertNotNull(f.manager.observeRootlessKeys())
            assertNull(observed.mediaKey)
            assertFalse(observed.hasRetainedMedia)
            assertFalse(f.manager.hasIdentityKey())
            assertEquals(0, f.writes)
            assertEquals(0, f.derivations)
        }

    @Test
    fun `cached independent media survives without a replacement root`() =
        runTest {
            val f = Fixture()
            val key = ByteArray(32) { 5 }
            f.storage.putBytes("media_payload_key_v1", key)
            val observed = assertNotNull(f.manager.observeRootlessKeys())
            assertContentEquals(key, observed.mediaKey)
            observed.mediaKey!!.fill(0)
            assertContentEquals(key, assertNotNull(f.manager.observeRootlessKeys()).mediaKey)
            assertEquals(0, f.writes)
            assertEquals(0, f.derivations)
        }

    @Test
    fun `retained media remains explicit rather than authorizing an empty plaintext classification`() =
        runTest {
            val f = Fixture()
            f.backup.writeMediaIdentity(MediaIdentityBackup("retained-binding", ByteArray(32) { 5 }))
            val observed = assertNotNull(f.manager.observeRootlessKeys())
            assertNull(observed.mediaKey)
            assertEquals(true, observed.hasRetainedMedia)
            assertEquals(0, f.writes)
        }

    @Test
    fun `an existing root is never classified as rootless`() =
        runTest {
            val f = Fixture()
            f.storage.putBytes("identity_key_v1", ByteArray(32) { 3 })
            assertNull(f.manager.observeRootlessKeys())
            assertEquals(0, f.writes)
        }

    @Test
    fun `secure or retained account ownership refuses rootless observation`() =
        runTest {
            val secure = Fixture()
            secure.storage.putString("identity_account_id_v1", "account")
            assertNull(secure.manager.observeRootlessKeys())
            val retained = Fixture(expectedOwner = { "account" })
            assertNull(retained.manager.observeRootlessKeys())
            assertEquals(0, secure.writes + retained.writes)
        }

    @Test
    fun `primary phrase and verification markers cannot be mistaken for a never-rooted journal`() =
        runTest {
            for (field in listOf("identity_recovery_phrase_v1", "identity_recovery_verified_v1")) {
                val f = Fixture()
                f.storage.putString(field, "")
                assertNull(f.manager.observeRootlessKeys())
                assertEquals(0, f.writes)
            }
        }

    @Test
    fun `retained root backups cannot be ignored or recovered during observation`() =
        runTest {
            val phrase = Fixture()
            phrase.backup.writePhrase("retained-phrase")
            assertNull(phrase.manager.observeRootlessKeys())
            val account = Fixture()
            account.backup.writeAccountIdentity(AccountIdentityBackup("account", ByteArray(32) { 3 }))
            assertNull(account.manager.observeRootlessKeys())
            assertEquals(0, phrase.writes + account.writes)
            assertEquals(0, phrase.derivations + account.derivations)
        }

    @Test
    fun `malformed present roots and cached media refuse instead of becoming absent`() =
        runTest {
            for (field in listOf("identity_key_v1", "media_payload_key_v1")) {
                for (value in listOf("", "not base64", "AQ==", Base64.encode(ByteArray(31)), Base64.encode(ByteArray(33)))) {
                    val f = Fixture()
                    f.storage.putString(field, value)
                    assertFailsWith<IllegalStateException> { f.manager.observeRootlessKeys() }
                    assertEquals(0, f.writes)
                }
            }
        }

    @Test
    fun `unavailable secure or retained storage propagates rather than granting rootless permission`() =
        runTest {
            val f = Fixture()
            val unavailable =
                object : SecureStorage by f.storage {
                    override suspend fun getString(key: String): String? = error("Synthetic unavailable vault")
                }
            assertFailsWith<IllegalStateException> { IdentityKeyManager(unavailable, FakeCryptoManager()).observeRootlessKeys() }
            val backup =
                object : IdentityKeyBackupStore by f.backup {
                    override suspend fun hasRootBackup(): Boolean = error("Synthetic unavailable recovery files")
                }
            assertFailsWith<IllegalStateException> { IdentityKeyManager(f.storage, FakeCryptoManager(), backup).observeRootlessKeys() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observation shares the identity mutation lock`() =
        runTest {
            val f = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val mutation =
                launch {
                    f.manager.withIdentityMutation {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            entered.await()
            var completed = false
            val reader =
                launch {
                    f.manager.observeRootlessKeys()
                    completed = true
                }
            try {
                runCurrent()
                assertFalse(completed)
            } finally {
                release.complete(Unit)
                mutation.join()
                reader.join()
            }
            assertEquals(true, completed)
        }

    @Test
    fun `observation diagnostics do not expose selected media`() =
        runTest {
            val f = Fixture()
            f.storage.putBytes("media_payload_key_v1", ByteArray(32) { 5 })
            val observed = assertNotNull(f.manager.observeRootlessKeys())
            assertFalse(observed.toString().contains("BQUFBQ"))
            assertFalse(observed.toString().contains("5, 5, 5"))
        }

    private class Fixture(
        expectedOwner: (suspend () -> String?)? = null,
    ) {
        val storage = InMemorySecureStorage()
        val backup = InMemoryIdentityKeyBackupStore()
        var writes = 0
        var derivations = 0
        private val readOnly =
            object : SecureStorage by storage {
                override suspend fun putString(
                    key: String,
                    value: String,
                ) {
                    writes++
                    error("Unexpected write")
                }

                override suspend fun remove(key: String) {
                    writes++
                    error("Unexpected removal")
                }

                override suspend fun clear() {
                    writes++
                    error("Unexpected clear")
                }
            }
        private val readOnlyBackup =
            object : IdentityKeyBackupStore by backup {
                override suspend fun writePhrase(phrase: String) {
                    writes++
                    error("Unexpected backup write")
                }

                override suspend fun writeAccountIdentity(identity: AccountIdentityBackup) {
                    writes++
                    error("Unexpected backup write")
                }

                override suspend fun writeMediaIdentity(identity: MediaIdentityBackup) {
                    writes++
                    error("Unexpected backup write")
                }

                override suspend fun clear() {
                    writes++
                    error("Unexpected backup clear")
                }
            }
        private val readOnlyCrypto =
            object : CryptoManager by FakeCryptoManager() {
                override suspend fun generateRecoveryPhrase(): List<String> {
                    derivations++
                    error("Unexpected mint")
                }

                override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
                    derivations++
                    error("Unexpected recovery")
                }
            }
        val manager = IdentityKeyManager(readOnly, readOnlyCrypto, readOnlyBackup, expectedAccountOwner = expectedOwner)
    }
}
