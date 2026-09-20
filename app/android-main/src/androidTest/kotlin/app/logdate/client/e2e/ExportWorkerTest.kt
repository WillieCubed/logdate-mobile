package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.export.ExportError
import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportOutcome
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
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `worker succeeds and emits completed file path when export completes`() =
        runTest {
            val mockUseCase =
                mockk<ExportArchiveUseCase> {
                    every { export(any(), any()) } answers {
                        secondArg<ArchiveContainer>().apply {
                            writeText("README.txt", "LogDate export")
                            writeText("manifest.json", "{}")
                            writeText("SHA256SUMS", "checksums")
                        }
                        flowOf(ArchiveExportProgress.Starting, ArchiveExportProgress.Completed(EMPTY_SUMMARY))
                    }
                }

            val recordingLauncher = RecordingExportLauncher()
            val destFile = File.createTempFile("export_test", ".zip", context.cacheDir)

            try {
                setupKoin(mockUseCase, recordingLauncher)

                val worker =
                    TestListenableWorkerBuilder<ExportWorker>(context)
                        .setInputData(
                            Data
                                .Builder()
                                .putString(ExportWorker.DESTINATION_URI_KEY, Uri.fromFile(destFile).toString())
                                .build(),
                        ).build()

                val result = worker.doWork()

                assertIs<ListenableWorker.Result.Success>(result)
                val completedUpdate = recordingLauncher.progressUpdates.lastOrNull { it.completedFilePath != null }
                assertNotNull(completedUpdate, "Expected a progress update with a completed file path")
                ZipFile(destFile).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toSet()
                    assertTrue("manifest.json" in names)
                    assertTrue("README.txt" in names)
                    assertTrue("SHA256SUMS" in names)
                    assertFalse("metadata.json" in names)
                }
            } finally {
                destFile.delete()
            }
        }

    @Test
    fun `worker fails when export progress emits failed`() =
        runTest {
            val mockUseCase =
                mockk<ExportArchiveUseCase> {
                    every { export(any(), any()) } returns
                        flowOf(ArchiveExportProgress.Starting, ArchiveExportProgress.Failed(ExportError.UNKNOWN))
                }

            setupKoin(mockUseCase, RecordingExportLauncher())

            val worker = TestListenableWorkerBuilder<ExportWorker>(context).build()
            val result = worker.doWork()

            assertIs<ListenableWorker.Result.Failure>(result)
        }

    @Test
    fun `worker fails when use case flow throws`() =
        runTest {
            val mockUseCase =
                mockk<ExportArchiveUseCase> {
                    every { export(any(), any()) } returns
                        flow { throw RuntimeException("Unexpected use case error") }
                }

            setupKoin(mockUseCase, RecordingExportLauncher())

            val worker = TestListenableWorkerBuilder<ExportWorker>(context).build()
            val result = worker.doWork()

            assertIs<ListenableWorker.Result.Failure>(result)
        }

    private fun setupKoin(
        exportUseCase: ExportArchiveUseCase,
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

private val EMPTY_SUMMARY =
    ArchiveExportSummary(
        counts = ArchiveCounts(0, 0, 0, 0, 0, 0, hasProfile = false),
        scope = ArchiveScope(complete = true),
    )

private fun ArchiveContainer.writeText(
    path: String,
    value: String,
) {
    entry(ArchivePath.of(path)) { sink ->
        val buffer = Buffer().writeUtf8(value)
        sink.write(buffer, buffer.size)
    }
}

private class RecordingExportLauncher : ExportLauncher {
    val progressUpdates = mutableListOf<ExportProgressInfo>()

    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    override fun startExport(options: ExportOptions) {}

    override fun cancelExport() {}

    override fun setExportCompletionCallback(callback: (ExportOutcome) -> Unit) {}

    override fun updateProgress(info: ExportProgressInfo) {
        progressUpdates += info
        _exportProgress.value = info
    }
}
