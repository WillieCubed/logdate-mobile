package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativePrivacyRepairIntentTest {
    @Test
    fun `completed proof repairs privacy without resetting content account or installation`() =
        fixture { f ->
            val before = f.markerBytes()
            assertTrue(f.intent().record(f.identity, f.cohort, HASH, f.protected()))
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertEquals("destination", f.store.data.value[f.device])
            assertFalse(f.detector.hasMissingNativePrivacyPayload())
            assertEquals(before, f.markerBytes())
            assertEquals("original encrypted journal", f.database.readText())
            assertFalse(f.intent().resume(), "Completed intent must not replay as a new delivery")
        }

    @Test
    fun `interruption after intent persistence resumes exact unknown before state`() =
        fixture { f ->
            f.interrupt("intent")
            assertNull(f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.intent().resume())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertEquals(f.identity.generation, f.detector.nativeRestoreSessionId())
        }

    @Test
    fun `interruption after privacy CAS resumes exact proposed after state`() =
        fixture { f ->
            f.interrupt("preferences")
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.intent().resume())
            assertFalse(f.detector.hasMissingNativePrivacyPayload())
            assertEquals(f.identity.accountDeliveryId, f.detector.pendingAccountRecoveryId())
        }

    @Test
    fun `interruption after privacy acknowledgement stays idempotent`() =
        fixture { f ->
            f.interrupt("acknowledged")
            val before = f.markerBytes()
            assertFalse(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.intent().resume())
            assertEquals(before, f.markerBytes())
            assertFalse(f.intent().resume())
        }

    @Test
    fun `independently known policy never gets overwritten by a retained repair`() =
        fixture { f ->
            f.interrupt("intent")
            f.store.data.value =
                f.store.data.value
                    .toMutablePreferences()
                    .apply { this[f.security] = "NONE" }
            assertFailsWith<IllegalStateException> { f.intent().resume() }
            assertEquals("NONE", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `changed protected journal is preserved and cannot borrow the old policy proof`() =
        fixture { f ->
            f.interrupt("intent")
            f.database.writeText("independent changed journal")
            assertFailsWith<IllegalStateException> { f.intent().resume() }
            assertEquals("independent changed journal", f.database.readText())
            assertNull(f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `changed incoming proof or native generation cannot resume`() =
        fixture { f ->
            f.interrupt("intent")
            f.incomingValid = false
            assertFailsWith<IllegalStateException> { f.intent().resume() }
            assertNull(f.store.data.value[f.security])
            f.incomingValid = true
            f.detector.beginNativeRestoreSession(retainPreferences = true)
            assertFailsWith<IllegalStateException> { f.intent().resume() }
            assertNull(f.store.data.value[f.security])
        }

    @Test
    fun `new sidecar changed key or changed installation identity rejects the old intent`() =
        runBlocking {
            for (name in listOf("logdate-wal", "files/db_recovery/passphrase_backup", "no-backup/.logdate_installation_id")) {
                fixture { f ->
                    f.interrupt("intent")
                    val changed =
                        File(f.root, name).apply {
                            parentFile?.mkdirs()
                            writeText("independent bytes")
                        }
                    assertFailsWith<IllegalStateException> { f.intent().resume() }
                    assertEquals("independent bytes", changed.readText())
                    assertNull(f.store.data.value[f.security])
                }
            }
        }

    @Test
    fun `same account resolution during interruption never reopens account recovery`() =
        fixture { f ->
            f.interrupt("intent")
            f.detector.acknowledgeAccountRecovery(f.identity.accountDeliveryId)
            val before = f.markerBytes()
            assertTrue(f.intent().resume())
            assertFalse(f.detector.isAccountRecoveryPending())
            assertEquals(before, f.markerBytes())
        }

    @Test
    fun `a changed source after policy persistence cannot acknowledge privacy`() =
        fixture { f ->
            assertFailsWith<IllegalStateException> {
                f.intent { if (it == "preferences") f.incomingValid = false }.record(f.identity, f.cohort, HASH, f.protected())
            }
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            f.incomingValid = true
            assertTrue(f.intent().resume())
        }

    @Test
    fun `a policy changed between CAS and acknowledgement stays independent and unacknowledged`() =
        fixture { f ->
            assertFailsWith<IllegalStateException> {
                f
                    .intent {
                        if (it == "preferences") {
                            f.store.data.value =
                                f.store.data.value
                                    .toMutablePreferences()
                                    .apply { this[f.security] = "NONE" }
                        }
                    }.record(f.identity, f.cohort, HASH, f.protected())
            }
            assertEquals("NONE", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `cancellation after durable policy leaves the receipt pending for exact resume`() =
        fixture { f ->
            coroutineScope {
                lateinit var operation: Deferred<Boolean>
                operation =
                    async(start = CoroutineStart.LAZY) {
                        f.intent { if (it == "preferences") operation.cancel() }.record(f.identity, f.cohort, HASH, f.protected())
                    }
                operation.start()
                assertFailsWith<CancellationException> { operation.await() }
            }
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.intent().resume())
        }

    @Test
    fun `a different source owner cannot create a repair intent`() =
        fixture { f ->
            f.incoming = f.incoming.toMutablePreferences().apply { this[stringPreferencesKey("identity.canonical_owner_id")] = "other" }
            assertFalse(f.intent().record(f.identity, f.cohort, HASH, f.protected()))
            assertFalse(f.intent().resume())
            assertNull(f.store.data.value[f.security])
        }

    @Test
    fun `received old privacy receipt cannot expose an unfinished policy write`() =
        fixture(privacyReceived = true) { f ->
            f.interrupt("preferences")
            assertFalse(f.detector.hasMissingNativePrivacyPayload(), "A file receipt can predate restored typed privacy fields")
            val guard = NativeRestorePrivacyGuard(f.context, f.store)
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> { guard.verifyBeforeContent { true } }
            assertTrue(f.intent().resume())
            guard.verifyBeforeContent { true }
        }

    @Test
    fun `receipt advancing before CAS protects the now accepted known policy`() =
        fixture { f ->
            f.store.data.value =
                f.store.data.value
                    .toMutablePreferences()
                    .apply { this[f.security] = "NONE" }
            f.interrupt("intent")
            assertTrue(f.detector.acknowledgePrivacyRepair(f.identity), "Simulate another completed authority accepting the current policy")
            assertFailsWith<IllegalStateException> { f.intent().resume() }
            assertEquals("NONE", f.store.data.value[f.security])
            assertFalse(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `source accounting runs only after acknowledged policy and before intent cleanup`() =
        fixture { f ->
            var accounted = 0
            f.accountSource = { acknowledgement ->
                accounted++
                assertEquals(f.identity.copy(privacyReceived = true), f.detector.capturePrivacyRepairIdentity())
                assertEquals("BIOMETRIC", f.store.data.value[f.security])
                assertEquals(nativePrivacyPreferenceFingerprint(f.store.data.value), acknowledgement.acceptedPreferencesSha256)
                assertEquals(f.protected(), acknowledgement.protectedInventory)
                assertTrue(hasPendingNativePrivacyRepair(f.context))
            }
            assertFalse(f.intent().resume(), "Historical sources cannot mint accounting authority")
            assertEquals(0, accounted)
            f.intent().record(f.identity, f.cohort, HASH, f.protected())
            assertEquals(1, accounted)
            assertFalse(hasPendingNativePrivacyRepair(f.context))
        }

    @Test
    fun `accounting interruption resumes from the same acknowledged source without losing intent`() =
        fixture { f ->
            var attempts = 0
            f.accountSource = {
                attempts++
                if (attempts == 1) throw IOException("Controlled accounting interruption")
            }
            assertFailsWith<IOException> { f.intent().record(f.identity, f.cohort, HASH, f.protected()) }
            assertTrue(hasPendingNativePrivacyRepair(f.context))
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertTrue(f.intent().resume())
            assertEquals(2, attempts)
            assertFalse(hasPendingNativePrivacyRepair(f.context))
        }

    private fun fixture(
        privacyReceived: Boolean = false,
        test: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val root = Files.createTempDirectory("privacy-repair-intent").toFile()
        try {
            test(Fixture(root, privacyReceived))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
        privacyReceived: Boolean,
    ) {
        val security = stringPreferencesKey("security_level")
        val device = stringPreferencesKey("device.id")
        val noBackup = File(root, "no-backup").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        val key =
            File(files, "db_recovery/passphrase_backup").apply {
                parentFile!!.mkdirs()
                writeText("original key")
            }
        val database = File(root, "logdate").apply { writeText("original encrypted journal") }
        val context =
            mockk<Context>().also { context ->
                every { context.noBackupFilesDir } returns noBackup
                every { context.filesDir } returns files
                every { context.applicationInfo } returns ApplicationInfo().apply { dataDir = root.absolutePath }
                every { context.getDatabasePath(any()) } answers { File(root, firstArg<String>()) }
            }
        val detector = PostRestoreDetector(context)
        val identity: NativePrivacyRepairIdentity
        val cohort = Uuid.random().toString()
        var incomingValid = true
        val store = Store(mutablePreferencesOf(stringPreferencesKey("identity.canonical_owner_id") to "owner", device to "destination"))
        var incoming: Preferences =
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to "owner",
                device to "source",
                security to "BIOMETRIC",
            )

        init {
            detector.beginNativeRestoreSession(retainPreferences = true)
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            if (privacyReceived) detector.markNativePrivacyPayloadReceived()
            detector.reserveAccountRecoveryForCompletion()
            detector.recordRestoreCompleted()
            identity = assertNotNull(detector.capturePrivacyRepairIdentity())
            File(noBackup, ".logdate_installation_id").writeText("destination-installation")
        }

        fun protected() =
            buildMap {
                for (name in listOf("logdate", "logdate-wal", "logdate-journal")) {
                    File(root, name).takeIf(File::exists)?.let { put("database/$name", hash(it)) }
                }
                put("files/db_recovery/passphrase_backup", hash(key))
                put("local/.logdate_installation_id", hash(File(noBackup, ".logdate_installation_id")))
            }

        private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

        var accountSource: suspend (NativePrivacyRepairAcknowledgement) -> Unit = {}

        fun intent(step: (String) -> Unit = {}) =
            NativePrivacyRepairIntent(context, store, { id, hash ->
                check(incomingValid && id == cohort && hash == HASH)
                incoming
            }, ::protected, accountSource = { accountSource(it) }, afterDurableStep = step)

        suspend fun interrupt(step: String) {
            assertFailsWith<IOException> {
                intent { if (it == step) throw IOException("Controlled interruption") }.record(identity, cohort, HASH, protected())
            }
        }

        fun markerBytes(): Map<String, List<Byte>> =
            noBackup
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.name != ".logdate_native_restore_privacy_received" &&
                        !it.name.startsWith(
                            "native-privacy-repair",
                        )
                }.associate { it.name to it.readBytes().toList() }
    }

    private class Store(
        initial: Preferences,
    ) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
