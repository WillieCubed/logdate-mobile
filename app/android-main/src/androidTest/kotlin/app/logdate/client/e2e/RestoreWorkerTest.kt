package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreProgressInfo
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.feature.core.restore.RestoreDiagnosticEvents
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Instrumented tests for [RestoreWorker].
 *
 * Verifies that the worker maps source URI availability and archive validity to the
 * correct WorkManager results, and that [RestoreLauncher.completeRestore] receives
 * the right outcome. Uses [TestListenableWorkerBuilder] with a Koin module wiring
 * fake/mock dependencies.
 */
/**
 * Instrumented tests for the [RestoreWorker] background task.
 *
 * This suite validates the [WorkManager] integration for data restoration,
 * ensuring the worker correctly handles ZIP archive validation, manages
 * dependencies via Koin, and reports its success or failure back to the
 * application's restoration launcher.
 */
@RunWith(AndroidJUnit4::class)
class RestoreWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        stopKoin()
    }

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `worker fails when source uri missing`() =
        runTest {
            val recordingLauncher = RecordingRestoreLauncher()
            setupKoin(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher)

            // No SOURCE_URI_KEY in input data — worker must fail immediately.
            val worker = TestListenableWorkerBuilder<RestoreWorker>(context).build()
            val result = worker.doWork()

            assertIs<ListenableWorker.Result.Failure>(result)
        }

    @Test
    fun `worker succeeds and completes with success when archive is valid`() =
        runTest {
            val archiveFile = createMinimalArchive(context)

            try {
                val testMetadata =
                    ExportMetadata(
                        exportDate = Instant.fromEpochMilliseconds(0),
                        userId = "test-user",
                        deviceId = "test-device",
                        appVersion = "1.0.0",
                        stats = ExportStats(journalCount = 1, noteCount = 2, draftCount = 0, mediaCount = 0),
                    )
                val restoreResult =
                    RestoreResult(
                        metadata = testMetadata,
                        journalsImported = 1,
                        notesImported = 2,
                        draftsImported = 0,
                        journalLinksImported = 1,
                        mediaImported = 0,
                        warnings = emptyList(),
                    )

                val mockUseCase =
                    mockk<RestoreUserDataUseCase> {
                        coEvery {
                            restore(
                                archive = any(),
                                options = any(),
                                mediaImporter = anyNullable(),
                                onProgress = anyNullable(),
                            )
                        } returns restoreResult
                    }
                val recordingLauncher = RecordingRestoreLauncher()
                val diagnosticEvents = RecordingRestoreDiagnostics()
                setupKoin(mockUseCase, mockk(relaxed = true), recordingLauncher, diagnosticEvents)
                val operationId = UUID.randomUUID().toString()

                val worker =
                    TestListenableWorkerBuilder<RestoreWorker>(context)
                        .setInputData(
                            androidx.work.Data
                                .Builder()
                                .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(archiveFile).toString())
                                .putBoolean(RestoreWorker.INCLUDE_DRAFTS_KEY, true)
                                .putBoolean(RestoreWorker.INCLUDE_MEDIA_KEY, false)
                                .putString(RestoreWorker.RESTORE_OPERATION_ID_KEY, operationId)
                                .build(),
                        ).build()

                val result = worker.doWork()

                assertIs<ListenableWorker.Result.Success>(result)
                val successOutcome = recordingLauncher.completedOutcomes
                    .filterIsInstance<RestoreOutcome.Success>()
                    .lastOrNull()
                assertNotNull(successOutcome, "Expected completeRestore to be called with Success")
                assertTrue(
                    successOutcome.summary.journalsImported == 1,
                    "Expected 1 journal imported, got ${successOutcome.summary.journalsImported}",
                )
                val completed = diagnosticEvents.events.single { it.phase == DiagnosticPhase.RESTORE && it.outcome == DiagnosticOutcome.SUCCEEDED }
                assertTrue(completed.operationId == operationId)
                assertNotNull(completed.attemptId)
            } finally {
                archiveFile.delete()
            }
        }

    @Test
    fun `worker fails and completes with failure when archive is invalid`() =
        runTest {
            val invalidFile = File.createTempFile("not_a_zip", ".zip", context.cacheDir)
            invalidFile.writeText("this is not a valid ZIP archive")

            try {
                val recordingLauncher = RecordingRestoreLauncher()
                val diagnosticEvents = RecordingRestoreDiagnostics()
                setupKoin(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher, diagnosticEvents)

                val worker =
                    TestListenableWorkerBuilder<RestoreWorker>(context)
                        .setInputData(
                            androidx.work.Data
                                .Builder()
                                .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(invalidFile).toString())
                                .build(),
                        ).build()

                val result = worker.doWork()

                assertIs<ListenableWorker.Result.Failure>(result)
                val failureOutcome = recordingLauncher.completedOutcomes
                    .filterIsInstance<RestoreOutcome.Failure>()
                    .lastOrNull()
                assertNotNull(failureOutcome, "Expected completeRestore to be called with Failure")
                assertTrue(diagnosticEvents.events.any { it.phase == DiagnosticPhase.RESTORE && it.outcome == DiagnosticOutcome.FAILED })
            } finally {
                invalidFile.delete()
            }
        }

    @Test
    fun `invalid cloud archive is deleted before restore returns`() = runTest {
        val cloudArchive = File.createTempFile("cloud-restore-test", ".zip", context.noBackupFilesDir)
        cloudArchive.writeText("invalid zip")
        val cachedBefore = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("logdate_restore") }.toSet()
        val recordingLauncher = RecordingRestoreLauncher()
        setupKoin(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher)
        val worker = TestListenableWorkerBuilder<RestoreWorker>(context)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(cloudArchive).toString())
                    .putBoolean(RestoreWorker.DELETE_SOURCE_AFTER_RESTORE_KEY, true)
                    .build(),
            ).build()

        try {
            assertIs<ListenableWorker.Result.Failure>(worker.doWork())
            assertFalse(cloudArchive.exists())
            assertTrue(context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("logdate_restore") }.toSet() == cachedBefore)
        } finally {
            cloudArchive.delete()
        }
    }

    private fun setupKoin(
        restoreUseCase: RestoreUserDataUseCase,
        mediaManager: MediaManager,
        restoreLauncher: RestoreLauncher,
        diagnostics: RestoreDiagnosticEvents? = null,
    ) {
        startKoin {
            modules(
                module {
                    factory { restoreUseCase }
                    single<MediaManager> { mediaManager }
                    single<RestoreLauncher> { restoreLauncher }
                    if (diagnostics != null) single<RestoreDiagnosticEvents> { diagnostics }
                },
            )
        }
    }
}

private class RecordingRestoreDiagnostics : RestoreDiagnosticEvents {
    val events = mutableListOf<SyncDiagnosticEvent>()
    private val source = DiagnosticSource(UploadScope("account", "https://first.example"), "epoch")

    override fun source(): DiagnosticSource = source

    override fun record(event: SyncDiagnosticEvent, source: DiagnosticSource?) {
        assertTrue(source === this.source)
        events += event
    }
}

/**
 * Creates a minimal valid ZIP archive in the app's cache directory containing all
 * required [ExportFileStructure] entries with empty JSON payloads. The caller is
 * responsible for deleting the file when done.
 */
private fun createMinimalArchive(context: Context): File {
    val file = File.createTempFile("restore_test_archive", ".zip", context.cacheDir)
    ZipOutputStream(FileOutputStream(file)).use { zip ->
        mapOf(
            ExportFileStructure.METADATA_FILE to
                """{"version":"1.2","exportDate":"1970-01-01T00:00:00Z","userId":"test","deviceId":"test","appVersion":"1.0","stats":{"journalCount":1,"noteCount":2,"draftCount":0,"mediaCount":0}}""",
            ExportFileStructure.JOURNALS_FILE to """{"journals":[]}""",
            ExportFileStructure.NOTES_FILE to """{"notes":[]}""",
            ExportFileStructure.JOURNAL_NOTES_FILE to """{"journal_notes":[]}""",
            ExportFileStructure.DRAFTS_FILE to """{"drafts":[]}""",
        ).forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return file
}

private class RecordingRestoreLauncher : RestoreLauncher {
    val completedOutcomes = mutableListOf<RestoreOutcome>()

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    override fun startFileSelection() {}

    override fun startRestore(options: ImportOptions) {}

    override fun cancelRestore() {}

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {}

    override fun setFileSelectedCallback(callback: (app.logdate.feature.core.restore.ArchiveFileInfo?) -> Unit) {}

    override fun updateProgress(info: RestoreProgressInfo) {
        _restoreProgress.value = info
    }

    override fun completeRestore(outcome: RestoreOutcome) {
        completedOutcomes += outcome
    }
}
