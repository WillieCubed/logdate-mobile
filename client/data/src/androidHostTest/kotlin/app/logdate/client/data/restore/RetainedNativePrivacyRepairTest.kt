package app.logdate.client.data.restore

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.verifiedNativeJournalSources
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real DataStore, source receipts and durable custody; native SQLCipher is the boundary double. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class RetainedNativePrivacyRepairTest {
    @Test
    fun `authenticated repair writes only two fields and survives a recreated retry`() =
        fixture {
            source()
            val original = store.data.first()
            val databaseBytes = database.readBytes()
            val keyBytes = keyFile.readBytes()
            val receipts = custodyFiles()
            assertTrue(repair().repairUnderDeliveryLock())
            val after = store.data.first()
            val expected =
                original.asMap() +
                    mapOf(
                        Pair<Preferences.Key<*>, Any>(ONBOARDED, true),
                        Pair<Preferences.Key<*>, Any>(SECURITY, "BIOMETRIC"),
                    )
            assertEquals(expected, after.asMap())
            assertContentEquals(databaseBytes, database.readBytes())
            assertContentEquals(keyBytes, keyFile.readBytes())
            assertEquals(receipts, custodyFiles())
            assertFalse(repair().repairUnderDeliveryLock())
            assertTrue(verifiedKeys.all { key -> key.all { it == 0.toByte() } })
            assertTrue(
                context.noBackupFilesDir
                    .listFiles()
                    .orEmpty()
                    .none { it.name.startsWith("native-privacy-repair-") },
            )
        }

    @Test
    fun `pending or unfinished native privacy context cannot be admitted`() =
        runBlocking {
            repeat(2) { variant ->
                fixture {
                    source()
                    detector.beginNativeRestoreSession()
                    if (variant == 1) detector.markNativePrivacyPayloadReceived()
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(SECURITY),
                    )
                    assertEquals(0, existingChecks)
                }
            }
        }

    @Test
    fun `any existing or invalid policy field is never overwritten`() =
        runBlocking {
            for (policy in listOf(
                Pair<Preferences.Key<*>, Any>(ONBOARDED, true),
                Pair<Preferences.Key<*>, Any>(SECURITY, "NONE"),
                Pair<Preferences.Key<*>, Any>(SECURITY, "invalid"),
            )) {
                fixture {
                    source()
                    store.updateData { it.toMutablePreferences().apply { setAny(policy.first, policy.second) } }
                    val before = store.data.first()
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertEquals(before, store.data.first())
                    assertEquals(0, existingChecks)
                }
            }
        }

    @Test
    fun `incoming recovery key cannot authorize a source rejected by the installed key`() =
        fixture {
            source()
            rejectSourceKey = true
            assertFalse(repair().repairUnderDeliveryLock())
            assertFalse(
                store.data
                    .first()
                    .asMap()
                    .containsKey(SECURITY),
            )
            assertTrue(verifiedKeys.isNotEmpty())
            assertTrue(verifiedKeys.all { key -> key.all { it == 0.toByte() } })
        }

    @Test
    fun `unreadable installed journal fails before source admission`() =
        fixture {
            source()
            rejectExisting = true
            assertFalse(repair().repairUnderDeliveryLock())
            assertTrue(verifiedKeys.isEmpty())
            assertFalse(
                store.data
                    .first()
                    .asMap()
                    .containsKey(SECURITY),
            )
        }

    @Test
    fun `encrypted source scope and exact preference checksum are mandatory`() =
        runBlocking {
            for (variant in 0..3) {
                fixture {
                    val id = source()
                    scopes[id] =
                        scopes.getValue(id).let {
                            when (variant) {
                                0 -> it.copy(ownerId = OTHER_OWNER)
                                1 -> it.copy(ownerBound = false)
                                2 -> it.copy(backendOrigin = "https://other.example")
                                else -> it.copy(preferencesSha256 = "0".repeat(64))
                            }
                        }
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(SECURITY),
                    )
                }
            }
        }

    @Test
    fun `two distinct authenticated receipts remain ambiguous even with the same policy`() =
        fixture {
            source()
            source()
            assertFalse(repair().repairUnderDeliveryLock())
            assertFalse(
                store.data
                    .first()
                    .asMap()
                    .containsKey(SECURITY),
            )
        }

    @Test
    fun `an exact duplicate source receipt is idempotent rather than ambiguous`() =
        fixture {
            val id = source()
            capture(sourceFiles.getValue(id))
            assertTrue(repair().repairUnderDeliveryLock())
        }

    @Test
    fun `incomplete and tampered custody cannot establish policy`() =
        runBlocking {
            repeat(2) { variant ->
                fixture {
                    source(incomplete = variant == 0)
                    if (variant ==
                        1
                    ) {
                        verifiedNativeJournalSources(
                            custody.sealedPayloads(),
                        ).values.single().getValue("files/logdate.preferences_pb").appendText("tampered")
                    }
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(SECURITY),
                    )
                }
            }
        }

    @Test
    fun `durable CAS refuses generation key scope policy and source changes`() =
        runBlocking {
            for (variant in 0..5) {
                fixture {
                    source()
                    val changing =
                        BeforeUpdateStore(store) { current ->
                            when (variant) {
                                0 -> {
                                    detector.beginNativeRestoreSession()
                                }

                                1 -> {
                                    keyFile.writeText(Base64.encode(ByteArray(32) { 7 }))
                                }

                                2 -> {
                                    return@BeforeUpdateStore current.toMutablePreferences().apply { this[OWNER] = OTHER_OWNER }
                                }

                                3 -> {
                                    return@BeforeUpdateStore current.toMutablePreferences().apply { this[SECURITY] = "NONE" }
                                }

                                4 -> {
                                    verifiedNativeJournalSources(
                                        custody.sealedPayloads(),
                                    ).values.single().getValue("database/logdate").appendText("changed")
                                }

                                else -> {
                                    return@BeforeUpdateStore current.toMutablePreferences().apply {
                                        this[BACKEND] =
                                            "https://other.example"
                                    }
                                }
                            }
                            current
                        }
                    assertFalse(repair(changing).repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(ONBOARDED),
                    )
                    if (variant ==
                        3
                    ) {
                        assertEquals("NONE", store.data.first()[SECURITY])
                    } else {
                        assertFalse(
                            store.data
                                .first()
                                .asMap()
                                .containsKey(SECURITY),
                        )
                    }
                }
            }
        }

    @Test
    fun `same installed key and exact unbound local owner can recover without adoption`() =
        fixture {
            store.updateData { it.toMutablePreferences().apply { remove(BOUND) } }
            source(ownerBound = null)
            assertTrue(repair().repairUnderDeliveryLock())
            assertFalse(
                store.data
                    .first()
                    .asMap()
                    .containsKey(BOUND),
            )
            assertEquals("BIOMETRIC", store.data.first()[SECURITY])
        }

    @Test
    fun `owner binding mismatch is rejected in both directions`() =
        runBlocking {
            for (bound in listOf("true", "false")) {
                fixture {
                    store.updateData { it.toMutablePreferences().apply { this[BOUND] = bound } }
                    source(ownerBound = if (bound == "true") "false" else "true")
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(SECURITY),
                    )
                }
            }
        }

    @Test
    fun `candidate incomplete or invalid privacy is never defaulted`() =
        runBlocking {
            for (variant in 0..2) {
                fixture {
                    val security =
                        when (variant) {
                            0 -> null
                            1 -> "invalid"
                            else -> "BIOMETRIC"
                        }
                    source(security = security, onboarded = if (variant == 2) null else true)
                    assertFalse(repair().repairUnderDeliveryLock())
                    assertFalse(
                        store.data
                            .first()
                            .asMap()
                            .containsKey(ONBOARDED),
                    )
                }
            }
        }

    @Test
    fun `a failed durable update leaves policy absent and a later retry succeeds`() =
        fixture {
            source()
            val refusing =
                object : DataStore<Preferences> by store {
                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        transform(store.data.first())
                        throw java.io.IOException("durable write failed")
                    }
                }
            assertFalse(repair(refusing).repairUnderDeliveryLock())
            assertFalse(
                store.data
                    .first()
                    .asMap()
                    .containsKey(SECURITY),
            )
            assertTrue(repair().repairUnderDeliveryLock())
        }

    private fun fixture(block: suspend Fixture.() -> Unit) =
        runBlocking {
            val fixture = Fixture()
            try {
                fixture.initialize()
                fixture.block()
            } finally {
                fixture.job.cancelAndJoin()
                check(fixture.root.deleteRecursively())
            }
        }

    private class Fixture {
        val root = createTempDirectory("retained-privacy-").toFile()
        val job = SupervisorJob()
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }

                override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
            }
        val database = context.getDatabasePath("logdate").apply { writeText("installed encrypted journal") }
        val key = ByteArray(32) { (it + 11).toByte() }
        val keyFile =
            File(context.filesDir, "db_recovery/passphrase_backup").apply {
                parentFile!!.mkdirs()
                writeText(Base64.encode(key))
            }
        val store =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                File(context.filesDir, "logdate.preferences_pb")
            }
        val detector = PostRestoreDetector(context)
        val custody = NativeJournalReplayCustody(context)
        val scopes = mutableMapOf<String, NativeJournalSnapshotScope>()
        val sourceFiles = mutableMapOf<String, Map<String, File>>()
        val verifiedKeys = mutableListOf<ByteArray>()
        var rejectSourceKey = false
        var rejectExisting = false
        var existingChecks = 0

        suspend fun initialize() {
            store.updateData { basePreferences() }
            detector.beginNativeRestoreSession()
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            detector.markNativePrivacyPayloadReceived()
            detector.recordRestoreCompleted()
        }

        fun repair(preferences: DataStore<Preferences> = store) =
            RetainedNativePrivacyRepair(
                context,
                preferences,
                verifyExisting = { existing, committed ->
                    existingChecks++
                    check(!rejectExisting && existing == database && committed == keyFile)
                    check(Base64.decode(committed.readText()).contentEquals(key))
                },
                verifySource = { source, installed ->
                    verifiedKeys += installed
                    check(!rejectSourceKey && installed.contentEquals(key))
                    scopes.getValue(source.readText())
                },
            )

        suspend fun source(
            incomplete: Boolean = false,
            ownerBound: String? = "true",
            security: String? = "BIOMETRIC",
            onboarded: Boolean? = true,
        ): String {
            val id = Uuid.random().toString()
            val folder = File(root, id).apply { mkdirs() }
            val prefs = File(folder, "logdate.preferences_pb")
            val writer = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { prefs }
            val snapshot =
                writer.updateData {
                    basePreferences().apply {
                        remove(BOUND)
                        if (ownerBound != null) this[BOUND] = ownerBound
                        if (onboarded != null) this[ONBOARDED] = onboarded
                        if (security != null) this[SECURITY] = security
                    }
                }
            val database = File(folder, "logdate").apply { writeText(id) }
            val receipt = File(folder, "receipt.json")
            writeNativeJournalSourceReceipt(receipt, snapshot, prefs, mapOf("database/logdate" to database))
            scopes[id] = NativeJournalSnapshotScope(OWNER_ID, ownerBound == "true", "https://cloud.example", nativeSnapshotChecksum(prefs))
            val files =
                mapOf("database/logdate" to database, "files/logdate.preferences_pb" to prefs, NATIVE_SOURCE_RECEIPT_PATH to receipt)
            sourceFiles[id] = files
            capture(files, incomplete)
            return id
        }

        fun capture(
            files: Map<String, File>,
            incomplete: Boolean = false,
        ) {
            val cohort = Uuid.random().toString()
            files.forEach { (path, file) ->
                file.inputStream().use { input ->
                    if (incomplete) {
                        kotlin.test.assertFailsWith<java.io.IOException> {
                            custody.captureInCohort(cohort, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length() + 1, input)
                        }
                    } else {
                        custody.captureInCohort(cohort, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length(), input)
                    }
                }
            }
            custody.finish()
        }

        fun custodyFiles(): Map<String, String> =
            File(context.noBackupFilesDir, "native-journal-replay-custody").walkTopDown().filter(File::isFile).associate {
                it.relativeTo(root).path to
                    nativeSnapshotChecksum(it)
            }
    }

    private class BeforeUpdateStore(
        private val delegate: DataStore<Preferences>,
        private val change: (Preferences) -> Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = delegate.data

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            delegate.updateData {
                transform(change(it))
            }
    }

    companion object {
        private const val OWNER_ID = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
        private const val OTHER_OWNER = "c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9"
        private val OWNER = stringPreferencesKey("identity.canonical_owner_id")
        private val BOUND = stringPreferencesKey("identity.canonical_owner_bound")
        private val BACKEND = stringPreferencesKey("backend_url")
        private val ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val SECURITY = stringPreferencesKey("security_level")

        private fun basePreferences() =
            mutablePreferencesOf(
                OWNER to OWNER_ID,
                BOUND to "true",
                BACKEND to "https://cloud.example",
                stringPreferencesKey("draft") to "untouched",
                stringPreferencesKey("device.id") to "installed-device",
            )

        private fun androidx.datastore.preferences.core.MutablePreferences.setAny(
            key: Preferences.Key<*>,
            value: Any,
        ) {
            if (key.name == ONBOARDED.name) this[ONBOARDED] = value as Boolean else this[SECURITY] = value as String
        }
    }
}
