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
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.feature.core.restore.RestoreError
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    private lateinit var originalBindings: RestoreWorkerTestBindings

    @Before
    fun setup() {
        originalBindings = RestoreWorkerTestBindings()
    }

    @After
    fun teardown() {
        originalBindings.restore()
    }

    @Test
    fun `worker fails when source uri missing`() =
        runTest {
            val recordingLauncher = RecordingRestoreLauncher()
            installRestoreWorkerTestBindings(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher)

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
                                onPendingMedia = anyNullable(),
                                mediaRecoveryId = any(),
                                expectedNoteScope = anyNullable(),
                            )
                        } returns restoreResult
                    }
                val recordingLauncher = RecordingRestoreLauncher()
                val diagnosticEvents = RecordingRestoreDiagnostics()
                installRestoreWorkerTestBindings(mockUseCase, mockk(relaxed = true), recordingLauncher, diagnosticEvents)
                val operationId = Uuid.random().toString()

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
                val successOutcome =
                    recordingLauncher.completedOutcomes
                        .filterIsInstance<RestoreOutcome.Success>()
                        .lastOrNull()
                assertNotNull(successOutcome, "Expected completeRestore to be called with Success")
                assertTrue(
                    successOutcome.summary.journalsImported == 1,
                    "Expected 1 journal imported, got ${successOutcome.summary.journalsImported}",
                )
                val completed =
                    diagnosticEvents.events.single {
                        it.phase == DiagnosticPhase.RESTORE &&
                            it.outcome == DiagnosticOutcome.SUCCEEDED
                    }
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
                installRestoreWorkerTestBindings(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher, diagnosticEvents)

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
                val failureOutcome =
                    recordingLauncher.completedOutcomes
                        .filterIsInstance<RestoreOutcome.Failure>()
                        .lastOrNull()
                assertNotNull(failureOutcome, "Expected completeRestore to be called with Failure")
                assertTrue(diagnosticEvents.events.any { it.phase == DiagnosticPhase.RESTORE && it.outcome == DiagnosticOutcome.FAILED })
            } finally {
                invalidFile.delete()
            }
        }

    @Test
    fun `invalid canonical location is rejected before recovery admission`() =
        runTest {
            val notes =
                """{"notes":[{"id":"10000000-0000-0000-0000-000000000001","type":"text","content":"Keep source","createdAt":"1970-01-01T00:00:00Z","updatedAt":"1970-01-01T00:00:00Z","locationDetails":{"place":{"id":"not-a-uuid"}}}]}"""
            val archive = createMinimalArchive(context, notesJson = notes)
            val sourceBytes = archive.readBytes()
            val useCase = mockk<RestoreUserDataUseCase>(relaxed = true)
            val launcher = RecordingRestoreLauncher()
            val diagnostics = RecordingRestoreDiagnostics()
            installRestoreWorkerTestBindings(useCase, mockk(relaxed = true), launcher, diagnostics)
            val recoveryId = Uuid.random().toString()
            try {
                val worker =
                    TestListenableWorkerBuilder<RestoreWorker>(context)
                        .setInputData(
                            androidx.work.workDataOf(
                                RestoreWorker.SOURCE_URI_KEY to Uri.fromFile(archive).toString(),
                                RestoreWorker.RECOVERY_ID_KEY to recoveryId,
                            ),
                        ).build()
                val result = assertIs<ListenableWorker.Result.Failure>(worker.doWork())
                assertEquals(RestoreError.INVALID_ARCHIVE.name, result.outputData.getString(RestoreWorker.ERROR_KEY))
                assertEquals(
                    RestoreError.INVALID_ARCHIVE,
                    launcher.completedOutcomes
                        .filterIsInstance<RestoreOutcome.Failure>()
                        .single()
                        .error,
                )
                assertFalse(context.filesDir.resolve("archive_recovery/records/$recoveryId.json").exists())
                assertFalse(
                    context.filesDir
                        .resolve("archive_recovery/sources")
                        .listFiles()
                        .orEmpty()
                        .any { it.name.startsWith(recoveryId) },
                )
                assertContentEquals(sourceBytes, archive.readBytes())
                coVerify(exactly = 0) {
                    useCase.restore(
                        archive = any(),
                        options = any(),
                        mediaImporter = anyNullable(),
                        onProgress = anyNullable(),
                        onPendingMedia = anyNullable(),
                        mediaRecoveryId = any(),
                        expectedNoteScope = anyNullable(),
                    )
                }
                assertTrue(
                    diagnostics.events.any {
                        it.outcome == DiagnosticOutcome.FAILED &&
                            it.reason == app.logdate.shared.model.diagnostics.DiagnosticReason.CORRUPT_PAYLOAD
                    },
                )
            } finally {
                archive.delete()
            }
        }

    @Test
    fun `invalid cloud archive is deleted before restore returns`() =
        runTest {
            val cloudArchive = File.createTempFile("cloud-restore-test", ".zip", context.noBackupFilesDir)
            cloudArchive.writeText("invalid zip")
            val cachedBefore =
                context.cacheDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.name.startsWith("logdate_restore") }
                    .toSet()
            val recordingLauncher = RecordingRestoreLauncher()
            installRestoreWorkerTestBindings(mockk(relaxed = true), mockk(relaxed = true), recordingLauncher)
            val worker =
                TestListenableWorkerBuilder<RestoreWorker>(context)
                    .setInputData(
                        androidx.work.Data
                            .Builder()
                            .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(cloudArchive).toString())
                            .putBoolean(RestoreWorker.DELETE_SOURCE_AFTER_RESTORE_KEY, true)
                            .build(),
                    ).build()

            try {
                assertIs<ListenableWorker.Result.Failure>(worker.doWork())
                assertFalse(cloudArchive.exists())
                assertTrue(
                    context.cacheDir
                        .listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith("logdate_restore") }
                        .toSet() == cachedBefore,
                )
            } finally {
                cloudArchive.delete()
            }
        }

    @Test
    fun `audio archive import cannot replace an accepted recording with the same name`() =
        runTest {
            val name = "archive-collision-${Uuid.random()}.wav"
            val accepted =
                File(context.filesDir, "audio_notes/$name").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            val incoming = "RIFF0000WAVEincoming archive recording".encodeToByteArray()
            val archive = createMinimalArchive(context, mapOf("media/audio/$name" to incoming))
            var restored: File? = null
            val expected =
                RestoreResult(
                    ExportMetadata(
                        exportDate = Instant.fromEpochMilliseconds(0),
                        userId = "synthetic",
                        deviceId = "synthetic",
                        appVersion = "1.0",
                        stats = ExportStats(0, 0, 0, 1),
                    ),
                    0,
                    0,
                    0,
                    0,
                    1,
                    emptyList(),
                )
            val useCase =
                mockk<RestoreUserDataUseCase> {
                    coEvery {
                        restore(
                            archive = any(),
                            options = any(),
                            mediaImporter = anyNullable(),
                            onProgress = anyNullable(),
                            onPendingMedia = anyNullable(),
                            mediaRecoveryId = any(),
                            expectedNoteScope = anyNullable(),
                        )
                    } coAnswers
                        {
                            val reference = thirdArg<MediaImporter>().importMedia("media/audio/$name")
                            restored = File(requireNotNull(Uri.parse(requireNotNull(reference)).path))
                            expected
                        }
                }
            try {
                installRestoreWorkerTestBindings(useCase, mockk(relaxed = true), RecordingRestoreLauncher())
                val worker =
                    TestListenableWorkerBuilder<RestoreWorker>(context)
                        .setInputData(
                            androidx.work.Data
                                .Builder()
                                .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(archive).toString())
                                .build(),
                        ).build()
                assertIs<ListenableWorker.Result.Success>(worker.doWork())
                val imported = assertNotNull(restored)
                assertNotEquals(accepted, imported)
                assertContentEquals(byteArrayOf(1, 2, 3), accepted.readBytes())
                assertContentEquals(incoming, imported.readBytes())
                assertEquals("audio_notes", imported.parentFile?.name)
            } finally {
                accepted.delete()
                restored?.delete()
                archive.delete()
            }
        }
}

/**
 * Creates a minimal valid ZIP archive in the app's cache directory containing all
 * required [ExportFileStructure] entries with empty JSON payloads. The caller is
 * responsible for deleting the file when done.
 */
private fun createMinimalArchive(
    context: Context,
    attachments: Map<String, ByteArray> = emptyMap(),
    notesJson: String = """{"notes":[]}""",
): File {
    val file = File.createTempFile("restore_test_archive", ".zip", context.cacheDir)
    ZipOutputStream(FileOutputStream(file)).use { zip ->
        mapOf(
            ExportFileStructure.METADATA_FILE to
                """{"version":"1.2","exportDate":"1970-01-01T00:00:00Z","userId":"test","deviceId":"test","appVersion":"1.0","stats":{"journalCount":1,"noteCount":2,"draftCount":0,"mediaCount":0}}""",
            ExportFileStructure.JOURNALS_FILE to """{"journals":[]}""",
            ExportFileStructure.NOTES_FILE to notesJson,
            ExportFileStructure.JOURNAL_NOTES_FILE to """{"journal_notes":[]}""",
            ExportFileStructure.DRAFTS_FILE to """{"drafts":[]}""",
        ).forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        attachments.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return file
}
