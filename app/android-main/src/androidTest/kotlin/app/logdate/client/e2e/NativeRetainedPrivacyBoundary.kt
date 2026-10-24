package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.restore.NativeAccountBackupAgent
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.captureNativeJournalDatabaseSnapshot
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeRestorePrivacyGuard
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.nativeRestoreDeliveryGate
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Exercises the real completion hook while its delivery lock still excludes reconciliation. */
internal object NativeRetainedPrivacyBoundary {
    suspend fun verify(parent: Context) {
        System.loadLibrary("sqlcipher")
        for (mode in Mode.entries) {
            val fixture = Fixture(parent, mode)
            try {
                fixture.seed()
                fixture.verifyBoundary()
            } finally {
                fixture.close()
            }
        }
    }

    private enum class Mode { VALID, WRONG_KEY, WRONG_PREFERENCE_HASH }

    private class Fixture(
        parent: Context,
        private val mode: Mode,
    ) {
        private val root = File(parent.noBackupFilesDir, "privacy-boundary-${Uuid.random()}").apply { check(mkdirs()) }
        private val context =
            object : ContextWrapper(parent) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }

                override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
            }
        private val key = ByteArray(32) { (it + 11).toByte() }
        private val sourceKey = if (mode == Mode.WRONG_KEY) ByteArray(32) { (it + 63).toByte() } else key.copyOf()
        private val store = createAndroidMainDataStore(context)
        private val sourceRoot = File(root, "source").apply { mkdirs() }
        private val owner = Uuid.random().toString()
        private val database = context.getDatabasePath("logdate")

        suspend fun seed() {
            createJournal(database, key)
            File(context.filesDir, "db_recovery/passphrase_backup").apply {
                parentFile!!.mkdirs()
                writeText(Base64.encode(key))
            }
            File(context.filesDir, "media/original").apply {
                parentFile!!.mkdirs()
                writeText("protected original bytes")
            }
            store.updateData { basePreferences() }
            val detector = PostRestoreDetector(context)
            detector.beginNativeRestoreSession()
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            detector.markNativePrivacyPayloadReceived()
            detector.recordRestoreCompleted()
            val preferences = File(sourceRoot, "logdate.preferences_pb")
            val snapshot = writePreferences(preferences)
            val sourceLive = File(sourceRoot, "live")
            createJournal(sourceLive, sourceKey)
            val sourceDatabase = File(sourceRoot, "snapshot")
            captureNativeJournalDatabaseSnapshot(sourceLive, sourceKey, sourceDatabase) { writeScope ->
                writeScope(
                    NativeJournalSnapshotScope(
                        owner,
                        false,
                        ORIGIN,
                        if (mode == Mode.WRONG_PREFERENCE_HASH) "0".repeat(64) else checksum(preferences),
                    ),
                )
            }
            val receipt = File(sourceRoot, "receipt.json")
            writeNativeJournalSourceReceipt(receipt, snapshot, preferences, mapOf("database/logdate" to sourceDatabase))
            val sourceKeyFile = File(sourceRoot, "incoming-key").apply { writeText(Base64.encode(sourceKey)) }
            val files =
                mapOf(
                    "database/logdate" to sourceDatabase,
                    "files/logdate.preferences_pb" to preferences,
                    "files/db_recovery/passphrase_backup" to sourceKeyFile,
                    NATIVE_SOURCE_RECEIPT_PATH to receipt,
                )
            val custody = NativeJournalReplayCustody(context)
            val cohort = Uuid.random().toString()
            files.forEach { (path, file) ->
                file.inputStream().use { custody.captureInCohort(cohort, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length(), it) }
            }
            custody.finish()
        }

        suspend fun verifyBoundary() {
            val agent = NativeAccountBackupAgent()
            ContextWrapper::class.java
                .getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java,
                ).apply { isAccessible = true }
                .invoke(agent, context)
            nativeRestoreDeliveryGate().withLock {
                val before = durableFiles()
                val beforePreferences = store.data.first()
                NativeAccountBackupAgent::class.java
                    .getDeclaredMethod("repairRetainedNativePrivacyUnderDeliveryLock")
                    .apply {
                        isAccessible =
                            true
                    }.invoke(agent)
                assertEquals(before, durableFiles(), "The completion hook must preserve DB, key, content, receipts and custody bytes")
                val after = store.data.first()
                if (mode == Mode.VALID) {
                    val expected =
                        beforePreferences.asMap() +
                            mapOf(
                                Pair<Preferences.Key<*>, Any>(ONBOARDED, true),
                                Pair<Preferences.Key<*>, Any>(SECURITY, "BIOMETRIC"),
                            )
                    assertEquals(expected, after.asMap())
                    NativeRestorePrivacyGuard(
                        context,
                        store,
                    ).verifyBeforeContent { error("Authenticated known policy must not query content") }
                } else {
                    assertEquals(beforePreferences, after)
                    assertFalse(after.asMap().containsKey(SECURITY))
                }
            }
            agent.onDestroy()
        }

        private suspend fun writePreferences(file: File): Preferences {
            val job = SupervisorJob()
            return try {
                val writer = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
                writer.updateData {
                    basePreferences().apply {
                        this[ONBOARDED] = true
                        this[SECURITY] = "BIOMETRIC"
                    }
                }
            } finally {
                job.cancelAndJoin()
            }
        }

        private suspend fun createJournal(
            file: File,
            passphrase: ByteArray,
        ) {
            file.parentFile!!.mkdirs()
            val openingKey = passphrase.copyOf()
            val database = getRoomDatabase(getDatabaseBuilder(context, openingKey, file), driver = null)
            try {
                database.openHelper.writableDatabase
                val created = Instant.parse("2026-01-01T00:00:00Z")
                database.textNoteDao().addNote(
                    TextNoteEntity(
                        content = "protected journal content",
                        lastUpdated = created,
                        created = created,
                    ),
                )
            } finally {
                database.close()
                openingKey.fill(0)
            }
        }

        private fun durableFiles(): Map<String, String> =
            root
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.canonicalFile != File(context.filesDir, "logdate.preferences_pb").canonicalFile
                }.associate {
                    it.relativeTo(root).path to
                        checksum(it)
                }

        private fun basePreferences() =
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to owner,
                stringPreferencesKey("backend_url") to ORIGIN,
                stringPreferencesKey("draft") to "protected draft",
                stringPreferencesKey("device.id") to "installed-device",
            )

        fun close() {
            key.fill(0)
            sourceKey.fill(0)
            assertTrue(root.deleteRecursively())
        }
    }

    private const val ORIGIN = "https://cloud.example"
    private val ONBOARDED = booleanPreferencesKey("is_onboarded")
    private val SECURITY = stringPreferencesKey("security_level")

    private fun checksum(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
