package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.export.ExportError
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportProgressInfo
import app.logdate.feature.core.export.ExportWorker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Instrumented tests for [ExportWorker].
 *
 * Verifies that the worker correctly maps use case outcomes to WorkManager results
 * and that the [ExportLauncher] receives the expected progress updates. Uses
 * [TestListenableWorkerBuilder] with a Koin module wiring fake/mock dependencies.
 */
@RunWith(AndroidJUnit4::class)
class ExportWorkerTest {
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
    fun workerSucceeds_andEmitsCompletedFilePath_whenExportCompletes() =
        runTest {
            val mockResult =
                mockk<ExportResult> {
                    every { serializeMetadata() } returns
                        """{"version":"1.2","exportDate":"1970-01-01T00:00:00Z","userId":"test","deviceId":"test","appVersion":"1.0","stats":{"journalCount":0,"noteCount":0,"draftCount":0,"mediaCount":0}}"""
                    every { serializeJournals() } returns """{"journals":[]}"""
                    every { serializeNotes() } returns """{"notes":[]}"""
                    every { serializeJournalNotes() } returns """{"journal_notes":[]}"""
                    every { serializeDrafts() } returns """{"drafts":[]}"""
                    every { serializeProfile() } returns null
                    every { serializePlaces() } returns null
                    every { serializeLocationHistory() } returns null
                    every { serializeMediaManifest() } returns null
                    every { mediaFiles } returns emptyList()
                    every { stats } returns ExportStats(0, 0, 0, 0)
                }

            val mockUseCase =
                mockk<ExportUserDataUseCase> {
                    every { exportUserData(any(), any(), any(), any(), anyNullable()) } returns
                        flowOf(ExportProgress.Starting, ExportProgress.Completed(mockResult))
                }

            val recordingLauncher = RecordingExportLauncher()
            val destFile = File.createTempFile("export_test", ".zip", context.cacheDir)

            try {
                setupKoin(mockUseCase, recordingLauncher)

                val worker =
                    TestListenableWorkerBuilder<ExportWorker>(context)
                        .setInputData(
                            androidx.work.Data
                                .Builder()
                                .putString(ExportWorker.DESTINATION_URI_KEY, Uri.fromFile(destFile).toString())
                                .build(),
                        ).build()

                val result = worker.doWork()

                assertIs<ListenableWorker.Result.Success>(result)
                val completedUpdate = recordingLauncher.progressUpdates.lastOrNull { it.completedFilePath != null }
                assertNotNull(completedUpdate, "Expected a progress update with a completed file path")
            } finally {
                destFile.delete()
            }
        }

    @Test
    fun workerFails_whenExportProgressEmitsFailed() =
        runTest {
            val mockUseCase =
                mockk<ExportUserDataUseCase> {
                    every { exportUserData(any(), any(), any(), any(), anyNullable()) } returns
                        flowOf(ExportProgress.Starting, ExportProgress.Failed(ExportError.UNKNOWN))
                }

            setupKoin(mockUseCase, RecordingExportLauncher())

            val worker = TestListenableWorkerBuilder<ExportWorker>(context).build()
            val result = worker.doWork()

            assertIs<ListenableWorker.Result.Failure>(result)
        }

    @Test
    fun workerFails_whenUseCaseFlowThrows() =
        runTest {
            val mockUseCase =
                mockk<ExportUserDataUseCase> {
                    every { exportUserData(any(), any(), any(), any(), anyNullable()) } returns
                        flow { throw RuntimeException("Unexpected use case error") }
                }

            setupKoin(mockUseCase, RecordingExportLauncher())

            val worker = TestListenableWorkerBuilder<ExportWorker>(context).build()
            val result = worker.doWork()

            assertIs<ListenableWorker.Result.Failure>(result)
        }

    private fun setupKoin(
        exportUseCase: ExportUserDataUseCase,
        exportLauncher: ExportLauncher,
    ) {
        startKoin {
            modules(
                module {
                    factory { exportUseCase }
                    single<ExportLauncher> { exportLauncher }
                },
            )
        }
    }
}

private class RecordingExportLauncher : ExportLauncher {
    val progressUpdates = mutableListOf<ExportProgressInfo>()

    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    override fun startExport(options: ExportOptions) {}

    override fun cancelExport() {}

    override fun setExportCompletionCallback(callback: (String?) -> Unit) {}

    override fun updateProgress(info: ExportProgressInfo) {
        progressUpdates += info
        _exportProgress.value = info
    }
}
