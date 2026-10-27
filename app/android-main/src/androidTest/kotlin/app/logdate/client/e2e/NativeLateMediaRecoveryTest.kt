package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.app.job.JobScheduler
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.data.restore.NativeAccountBackupAgent
import app.logdate.client.data.restore.scheduleRetainedJournalReconciliation
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.captureNativeJournalDatabaseSnapshot
import app.logdate.client.database.getDatabaseBuilder
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.retainedJournalWorkspace
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.media.storage.androidMediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.core.export.ZipStreamArchiveContainer
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Seeds a verified snapshot for production completion, then tests late callbacks and passive startup.
 * The synthetic seed does not claim Android transport or platform extraction-filter acceptance.
 */
@RunWith(AndroidJUnit4::class)
class NativeLateMediaRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private val fixture get() = File(context.noBackupFilesDir, "native-late-media-runtime")
    private val stateFile get() = File(fixture, "state.json")

    @Test
    fun seedCompletedTransferAndDeliverOnlyMissingOriginal() =
        runBlocking {
            requireStage("seed")
            RetainedJournalMergeFixtureSupport.requireAcceptanceDevice(context)
            check(!context.getDatabasePath("logdate").exists() && !fixture.exists()) {
                "A fresh managed installation is required; existing journal data must remain untouched"
            }
            restoreCompletedTransfer()
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitReady()
                assertNull(retainedJournalWorkspace(context).current())
                val bytes = RetainedJournalMergeFixtureSupport.pngFixture()
                val media = koin.get<MediaManager>()
                val mediaRef = media.saveMedia(MediaPayload("late-original.png", "image/png", bytes.size.toLong(), bytes))
                val resolver = androidMediaFileResolver(context)
                val canonical = resolver.storedReference(mediaRef)
                assertTrue(canonical.startsWith("logdate-media://library/objects/sha256/"))
                val original = File(assertNotNull(resolver.filePath(canonical)))
                val now = Clock.System.now()
                val journalId = Uuid.random()
                val noteId = Uuid.random()
                koin.get<JournalRepository>().create(
                    Journal(id = journalId, title = "Late original recovery", created = now, lastUpdated = now),
                )
                val marker = UnavailableMediaReferences.nativeRestore(canonical)
                koin.get<JournalNotesRepository>().create(JournalNote.Image(noteId, now, now, marker), journalId)
                check(original.delete()) { "Only this test's synthetic original may be removed" }
                awaitIdle()
                interruptScheduledReconciliation {
                    deliverLateOriginal(original, bytes)
                    assertFalse(original.exists(), "Completed callback must retain the original without writing live media")
                    assertTrue(NativeJournalReplayCustody(context).hasInputs())
                    assertEquals(marker, (koin.get<JournalNotesRepository>().getNoteById(noteId) as JournalNote.Image).mediaRef)
                    saveFixtureState(noteId, journalId, canonical, bytes, now.toEpochMilliseconds())
                }
            }
        }

    @Test
    fun restartRepairsRealRepositoryAndAcknowledgesCustodyIdempotently() =
        runBlocking {
            requireStage("verify")
            RetainedJournalMergeFixtureSupport.requireAcceptanceDevice(context)
            val state = JSONObject(stateFile.readText())
            assertTrue(state.getInt("pid") != Process.myPid(), "Run verify in a new app process after the seed invocation")
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitReady()
                assertNull(retainedJournalWorkspace(context).current(), "Media-only recovery must not require a WORKING generation")
                awaitRecovered(state)
                val notes = koin.get<JournalNotesRepository>()
                val expected = assertNotNull(notes.getNoteById(Uuid.parse(state.getString("noteId"))))
                awaitIdle()
                assertTrue(scheduleRetainedJournalReconciliation(context))
                awaitIdle()
                awaitRecovered(state)
                assertEquals(expected, notes.getNoteById(expected.uid), "A second reconciliation must preserve the existing note")
                assertEquals(1, notes.allNotesObserved.first().count { it.uid == expected.uid })
                assertTrue(koin.get<JournalRepository>().getJournalById(Uuid.parse(state.getString("journalId"))) != null)
            }
        }

    @Test
    fun consumedOriginalExportsWithoutUnreadableOmissions() =
        runBlocking {
            requireStage("export")
            RetainedJournalMergeFixtureSupport.requireAcceptanceDevice(context)
            val state = JSONObject(stateFile.readText())
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitReady()
                awaitRecovered(state)
                val archive = File.createTempFile("native-retained-export-", ".zip", context.cacheDir)
                try {
                    ZipOutputStream(archive.outputStream()).use { zip ->
                        val completed =
                            assertIs<ArchiveExportProgress.Completed>(
                                koin.get<ExportArchiveUseCase>().export(ArchiveExportOptions(), ZipStreamArchiveContainer(zip)).last(),
                            )
                        assertFalse(
                            completed.summary.scope.omitted
                                .any { it.reason == ArchiveOmissionReason.UNREADABLE },
                        )
                    }
                    ZipFile(archive).use { zip ->
                        assertTrue(
                            zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("media/") }.any { entry ->
                                state.getString("sha256") ==
                                    RetainedJournalMergeFixtureSupport.sha256(zip.getInputStream(entry).use { input -> input.readBytes() })
                            },
                            "The actual export must retain the recovered original bytes",
                        )
                    }
                } finally {
                    check(archive.delete())
                }
            }
        }

    private suspend fun restoreCompletedTransfer() {
        check(fixture.mkdirs())
        val owner = Uuid.random().toString()
        val origin = DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        val sourcePreferences = File(fixture, "source.preferences_pb")
        val writerJob = SupervisorJob()
        val preferences =
            try {
                PreferenceDataStoreFactory
                    .create(scope = CoroutineScope(writerJob + Dispatchers.IO), produceFile = { sourcePreferences })
                    .updateData {
                        mutablePreferencesOf(
                            stringPreferencesKey("identity.canonical_owner_id") to owner,
                            stringPreferencesKey("backend_url") to origin,
                        )
                    }
            } finally {
                writerJob.cancelAndJoin()
            }
        val key = ByteArray(32) { (it + 1).toByte() }
        val seedDatabase = File(fixture, "seed-logdate")
        val database = getRoomDatabase(getDatabaseBuilder(context, key.copyOf(), seedDatabase), driver = null)
        try {
            database.openHelper.writableDatabase
        } finally {
            database.close()
        }
        val sourceDatabase = File(fixture, "source-logdate")
        captureNativeJournalDatabaseSnapshot(seedDatabase, key, sourceDatabase) { writeScope ->
            writeScope(
                NativeJournalSnapshotScope(owner, false, origin, RetainedJournalMergeFixtureSupport.sha256(sourcePreferences.readBytes())),
            )
        }
        val receipt = File(fixture, "source-receipt")
        writeNativeJournalSourceReceipt(receipt, preferences, sourcePreferences, mapOf("database/logdate" to sourceDatabase))
        val portableKey = File(fixture, "source-key").apply { writeText(Base64.encode(key)) }
        key.fill(0)
        val inputs =
            mapOf(
                "database/logdate" to sourceDatabase,
                "files/logdate.preferences_pb" to sourcePreferences,
                "files/db_recovery/passphrase_backup" to portableKey,
                NATIVE_SOURCE_RECEIPT_PATH to receipt,
            )
        val agent = callbackAgent()
        try {
            // Model the already-delivered canonical preference bytes without bypassing the
            // production acceptance check. Capture vacancy before this synthetic delivery.
            PostRestoreDetector(context).beginNativeRestoreSession(retainPreferences = true)
            val authority = NativeJournalRestoreAuthority(context)
            authority.begin(resumeInterrupted = false)
            val canonicalPreferences = File(context.filesDir, "logdate.preferences_pb")
            sourcePreferences.inputStream().use { input ->
                FileOutputStream(canonicalPreferences).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            assertEquals(nativeFileSha256(sourcePreferences), nativeFileSha256(canonicalPreferences))
            authority.recordFrameworkPortableState()
            for ((path, source) in inputs) {
                val destination = File(context.filesDir, (NATIVE_JOURNAL_TRANSFER_PREFIX + path).removePrefix("files/"))
                // API34 does not expose the framework D2D destination selector to instrumentation.
                // Seed its owned immutable subtree directly; completion still validates and installs
                // through the production manifest agent. This does not test platform filtering.
                check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                source.inputStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                assertTrue(destination.isFile, "The owned snapshot must contain $path at $destination")
                assertEquals(nativeFileSha256(source), nativeFileSha256(destination), "Delivered snapshot bytes must match $path")
            }
            agent.onRestoreFinished()
        } finally {
            agent.onDestroy()
        }
        assertNotNull(NativeJournalRestoreAuthority(context).completedSourceReceipt())
        assertFalse(File(context.filesDir, "native_journal_transfer/current").exists())
    }

    private fun deliverLateOriginal(
        destination: File,
        bytes: ByteArray,
    ) {
        val source = File(fixture, "incoming-original").apply { writeBytes(bytes) }
        val agent = callbackAgent()
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                agent.onRestoreFile(descriptor, bytes.size.toLong(), destination, BackupAgent.TYPE_FILE, 0x180L, 0L)
            }
            agent.onRestoreFinished()
        } finally {
            agent.onDestroy()
        }
    }

    /** Cancels the scheduled worker behind its real lock to model interruption before repair. */
    private suspend fun interruptScheduledReconciliation(block: suspend () -> Unit) {
        val mutex =
            Class
                .forName("app.logdate.client.data.restore.RetainedJournalReconciliationLock")
                .getDeclaredField("mutex")
                .apply { isAccessible = true }
                .get(null) as Mutex
        val scheduler = context.getSystemService(JobScheduler::class.java)
        withTimeout(15_000L) { mutex.lock() }
        try {
            block()
            assertTrue(scheduler.allPendingJobs.any { it.id == 0x4e4a5235 }, "The production callback must schedule reconciliation")
        } finally {
            scheduler.cancel(0x4e4a5235)
            mutex.unlock()
        }
        assertTrue(NativeJournalReplayCustody(context).hasInputs(), "Startup must discover the interrupted delivery")
    }

    private fun saveFixtureState(
        noteId: Uuid,
        journalId: Uuid,
        canonical: String,
        bytes: ByteArray,
        createdAt: Long,
    ) {
        val state =
            JSONObject()
                .put("pid", Process.myPid())
                .put("noteId", noteId.toString())
                .put("canonical", canonical)
                .put("sha256", RetainedJournalMergeFixtureSupport.sha256(bytes))
                .put("createdAt", createdAt)
                .put("journalId", journalId.toString())
        RetainedJournalMergeFixtureSupport.writeDurably(stateFile, state.toString())
    }

    private suspend fun awaitIdle() {
        withTimeout(90_000L) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            while (scheduler.allPendingJobs.any { it.id == 0x4e4a5235 }) delay(200)
        }
    }

    private suspend fun awaitRecovered(state: JSONObject) {
        withTimeout(90_000L) {
            val notes = koin.get<JournalNotesRepository>()
            val media = koin.get<MediaManager>()
            while (true) {
                val note = notes.getNoteById(Uuid.parse(state.getString("noteId"))) as? JournalNote.Image
                if (note != null &&
                    !UnavailableMediaReferences.isUnavailable(note.mediaRef) &&
                    !NativeJournalReplayCustody(context).hasInputs()
                ) {
                    assertEquals(state.getLong("createdAt"), note.creationTimestamp.toEpochMilliseconds())
                    val bytes = media.readMedia(note.mediaRef).data
                    assertEquals(state.getString("sha256"), RetainedJournalMergeFixtureSupport.sha256(bytes))
                    val bitmap =
                        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size), "Recovered original must decode offline")
                    try {
                        assertEquals(128, bitmap.width)
                        assertEquals(96, bitmap.height)
                    } finally {
                        bitmap.recycle()
                    }
                    return@withTimeout
                }
                delay(200)
            }
        }
    }

    private suspend fun awaitReady() {
        withTimeout(60_000L) { koin.get<DatabaseStartupMonitor>().state.first { it == DatabaseStartupState.Ready } }
    }

    private fun callbackAgent(): NativeBackupAgent =
        NativeAccountBackupAgent().also { agent ->
            // Use the manifest agent, including its production persisted scheduling hook.
            ContextWrapper::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }
                .invoke(agent, context)
            agent.onCreate()
        }

    private fun nativeFileSha256(file: File): String = RetainedJournalMergeFixtureSupport.sha256(file.readBytes())

    private fun requireStage(stage: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeLateMediaStage") == stage)
    }
}
