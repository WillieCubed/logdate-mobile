package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExistingIdentityKeySnapshotTest {
    @Test
    fun `missing root cannot mint or recover from retained backup`() =
        runTest {
            val f = Fixture()
            f.backup.writeAccountIdentity(AccountIdentityBackup("account", f.root, f.media))
            f.storage.putString("identity_account_id_v1", "account")
            assertNull(f.read("account"))
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
            assertFalse(f.manager.hasIdentityKey())
            assertTrue(
                f.backup
                    .readAccountIdentity()!!
                    .key
                    .contentEquals(f.root),
            )
        }

    @Test
    fun `empty installation cannot create a local identity`() =
        runTest {
            val f = Fixture()
            assertNull(f.read(null))
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
            assertFalse(f.manager.hasIdentityKey())
        }

    @Test
    fun `account observation preserves exact cached legacy media without writes`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            val observed = assertNotNull(f.read("account"))
            assertEquals("account", observed.accountId)
            assertEquals(ExistingMediaKeySource.CACHED, observed.mediaKeySource)
            assertTrue(observed.identityKey.contentEquals(f.root))
            assertTrue(observed.mediaKey.contentEquals(f.media))
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
            assertFalse(f.backup.hasBackup())
        }

    @Test
    fun `local observation preserves unbound owner and never invents an account`() =
        runTest {
            val f = Fixture()
            f.seed(null, cached = f.media)
            val observed = assertNotNull(f.read(null))
            assertNull(observed.accountId)
            assertTrue(observed.identityKey.contentEquals(f.root))
            assertTrue(observed.mediaKey.contentEquals(f.media))
            assertNull(f.storage.getString("identity_account_id_v1"))
            assertEquals(0, f.writes)
        }

    @Test
    fun `account and local ownership cannot be silently rebound`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            assertNull(f.read("other"))
            assertNull(f.read(null))
            f.storage.remove("identity_account_id_v1")
            assertNull(f.read("account"))
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
        }

    @Test
    fun `retained journal owner must match even when secure owner matches`() =
        runTest {
            val f = Fixture(expectedOwner = { "other" })
            f.seed("account", cached = f.media)
            assertNull(f.read("account"))
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
        }

    @Test
    fun `retained legacy media selection wins over derivation`() =
        runTest {
            val f = Fixture()
            f.seed("account")
            val binding = IdentityBackupCoordinator(f.storage, f.crypto, f.backup).binding(f.root)
            f.backup.writeMediaIdentity(MediaIdentityBackup(binding, f.media))
            val observed = assertNotNull(f.read("account"))
            assertTrue(observed.mediaKey.contentEquals(f.media))
            assertEquals(ExistingMediaKeySource.RETAINED, observed.mediaKeySource)
            assertEquals(0, f.derived)
            assertEquals(0, f.writes)
            assertNull(f.storage.getString("media_payload_key_v1"))
        }

    @Test
    fun `derived existing-root fallback is pure and does not backfill storage`() =
        runTest {
            val f = Fixture()
            f.seed("account")
            val observed = assertNotNull(f.read("account"))
            assertTrue(observed.mediaKey.contentEquals(f.derivedMedia))
            assertEquals(1, f.derived)
            assertEquals(0, f.writes)
            assertNull(f.storage.getString("media_payload_key_v1"))
            assertFalse(f.backup.hasBackup())
        }

    @Test
    fun `malformed primary and cached keys cannot select a replacement`() =
        runTest {
            for (name in listOf("identity_key_v1", "media_payload_key_v1")) {
                val f = Fixture()
                f.seed("account", cached = f.media)
                f.storage.putBytes(name, ByteArray(31))
                assertFailsWith<IllegalStateException> { f.read("account") }
                assertEquals(0, f.derived)
                assertEquals(0, f.writes)
            }
        }

    @Test
    fun `conflicting retained root or media is refused without derivation`() =
        runTest {
            for (conflict in listOf("root", "media")) {
                val f = Fixture()
                f.seed("account", cached = f.media)
                f.backup.writeAccountIdentity(
                    AccountIdentityBackup(
                        "account",
                        if (conflict == "root") ByteArray(32) { 8 } else f.root,
                        if (conflict == "media") ByteArray(32) { 8 } else f.media,
                    ),
                )
                assertFailsWith<IllegalStateException> { f.read("account") }
                assertEquals(0, f.derived)
                assertEquals(0, f.writes)
            }
        }

    @Test
    fun `conflicting primary phrase is refused without modifying it`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            f.storage.putString("identity_recovery_phrase_v1", (1..12).joinToString(" ") { "other-$it" })
            assertFailsWith<IllegalStateException> { f.read("account") }
            assertEquals(0, f.writes)
            assertEquals(0, f.derived)
        }

    @Test
    fun `invalid pure derivation cannot return an accepted snapshot`() =
        runTest {
            val f = Fixture()
            f.seed("account")
            assertFailsWith<IllegalStateException> { f.manager.snapshotExistingKeys("account") { ByteArray(31) } }
            assertEquals(0, f.writes)
        }

    @Test
    fun `returned arrays cannot mutate storage or retained keys and debug output is redacted`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            val snapshot = assertNotNull(f.read("account"))
            snapshot.identityKey.fill(9)
            snapshot.mediaKey.fill(9)
            val fresh = assertNotNull(f.read("account"))
            assertTrue(fresh.identityKey.contentEquals(f.root))
            assertTrue(fresh.mediaKey.contentEquals(f.media))
            assertEquals("ExistingIdentityKeySnapshot([REDACTED])", fresh.toString())
            assertEquals(0, f.writes)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observation shares mutation lock and rechecks owner after acquiring it`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            val locked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder =
                launch {
                    f.manager.withIdentityMutation {
                        locked.complete(Unit)
                        release.await()
                        f.storage.putString("identity_account_id_v1", "other")
                    }
                }
            locked.await()
            var observed: ExistingIdentityKeySnapshot? = null
            var completed = false
            val reader =
                launch {
                    observed = f.read("account")
                    completed = true
                }
            runCurrent()
            assertFalse(completed)
            release.complete(Unit)
            holder.join()
            reader.join()
            assertNull(observed)
            assertTrue(completed)
            assertEquals(0, f.writes)
        }

    @Test
    fun `present blank or malformed media cache cannot select a derived replacement`() =
        runTest {
            for (raw in listOf("", " ", "not base64")) {
                val f = Fixture()
                f.seed("account")
                f.storage.putString("media_payload_key_v1", raw)
                assertFailsWith<IllegalStateException> { f.read("account") }
                assertEquals(0, f.derived)
                assertEquals(0, f.writes)
                assertEquals(raw, f.storage.getString("media_payload_key_v1"))
            }
        }

    @Test
    fun `unknown unreadable root backup cannot be excused by valid retained media`() =
        runTest {
            val f = Fixture()
            f.seed("account", cached = f.media)
            val unknown =
                object : IdentityKeyBackupStore by f.backup {
                    override suspend fun hasRootBackup() = true
                }
            val manager = IdentityKeyManager(f.storage, f.crypto, unknown)
            assertFailsWith<IllegalStateException> { manager.snapshotExistingKeys("account") { error("Unexpected derivation") } }
            assertTrue(manager.getIdentityKey().contentEquals(f.root))
        }

    @Test
    fun `derivation callback cannot change the observed existing root`() =
        runTest {
            val f = Fixture()
            f.seed("account")
            val observed =
                assertNotNull(
                    f.manager.snapshotExistingKeys("account") { root ->
                        root.fill(9)
                        f.derivedMedia.copyOf()
                    },
                )
            assertTrue(observed.identityKey.contentEquals(f.root))
            assertEquals(ExistingMediaKeySource.DERIVED_EXISTING_ROOT, observed.mediaKeySource)
            assertEquals(0, f.writes)
        }

    private class Fixture(
        expectedOwner: (suspend () -> String?)? = null,
    ) {
        val storage = InMemorySecureStorage()
        val crypto = FakeCryptoManager()
        val backup = InMemoryIdentityKeyBackupStore()
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
        val root = ByteArray(32) { 3 }
        val media = ByteArray(32) { 5 }
        val derivedMedia = ByteArray(32) { 7 }
        var writes = 0
        var derived = 0
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
        val manager = IdentityKeyManager(readOnly, crypto, readOnlyBackup, expectedAccountOwner = expectedOwner)

        suspend fun seed(
            owner: String?,
            cached: ByteArray? = null,
        ) {
            storage.putBytes("identity_key_v1", root)
            if (owner != null) storage.putString("identity_account_id_v1", owner)
            if (cached != null) storage.putBytes("media_payload_key_v1", cached)
        }

        suspend fun read(owner: String?): ExistingIdentityKeySnapshot? =
            manager.snapshotExistingKeys(owner) {
                derived++
                assertTrue(it.contentEquals(root))
                derivedMedia.copyOf()
            }
    }
}
