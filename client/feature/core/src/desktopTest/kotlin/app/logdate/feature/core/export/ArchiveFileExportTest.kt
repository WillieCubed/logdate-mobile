package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportError
import app.logdate.client.domain.export.ExportStage
import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import okio.buffer
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArchiveFileExportTest {
    private val folder = Files.createTempDirectory("archive-file-export").toFile()
    private val target = File(folder, "logdate_export.zip")
    private val summary = ArchiveExportSummary(ArchiveCounts(1, 1, 0, 0, 0, 0, hasProfile = false), ArchiveScope(complete = true))

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun ArchiveContainer.write(
        path: String,
        text: String,
    ) = entry(ArchivePath.of(path), compress = true) { sink -> sink.buffer().apply { writeUtf8(text) }.flush() }

    private fun exporting(
        finish: suspend (FlowCollector<ArchiveExportProgress>) -> Unit,
    ): (ArchiveContainer) -> Flow<ArchiveExportProgress> =
        { container ->
            flow {
                emit(ArchiveExportProgress.Starting)
                container.write("README.txt", "new archive")
                finish(this)
            }
        }

    private fun remainingFiles() = folder.list().orEmpty().sorted()

    @Test
    fun `a completed export creates the file and leaves no temporary file beside it`() =
        runTest {
            val outcome = exportArchiveToFile(target, exporting { it.emit(ArchiveExportProgress.Completed(summary)) }) {}

            assertEquals(ArchiveFileOutcome.Completed(summary), outcome)
            assertEquals(listOf(target.name), remainingFiles())
            ZipFile(target).use { assertEquals("new archive", it.getInputStream(it.getEntry("README.txt")).readBytes().decodeToString()) }
        }

    @Test
    fun `a completed export replaces the file that was already there`() =
        runTest {
            target.writeText("earlier export")

            exportArchiveToFile(target, exporting { it.emit(ArchiveExportProgress.Completed(summary)) }) {}

            ZipFile(target).use { assertTrue(it.getEntry("README.txt") != null) }
            assertEquals(listOf(target.name), remainingFiles())
        }

    @Test
    fun `a failed export leaves the file that was already there untouched`() =
        runTest {
            target.writeText("earlier export")

            val outcome = exportArchiveToFile(target, exporting { it.emit(ArchiveExportProgress.Failed(ExportError.UNKNOWN)) }) {}

            assertIs<ArchiveFileOutcome.Failed>(outcome)
            assertEquals("earlier export", target.readText())
            assertEquals(listOf(target.name), remainingFiles())
        }

    @Test
    fun `a cancelled export leaves the file that was already there untouched and stays cancelled`() =
        runTest {
            target.writeText("earlier export")

            assertFailsWith<CancellationException> {
                exportArchiveToFile(target, exporting { throw CancellationException("cancelled") }) {}
            }

            assertEquals("earlier export", target.readText())
            assertEquals(listOf(target.name), remainingFiles())
        }

    @Test
    fun `an error while writing leaves the file that was already there untouched and is passed on`() =
        runTest {
            target.writeText("earlier export")

            assertFailsWith<IOException> { exportArchiveToFile(target, exporting { throw IOException("disk full") }) {} }

            assertEquals("earlier export", target.readText())
            assertEquals(listOf(target.name), remainingFiles())
        }

    @Test
    fun `a failed export with no file there leaves nothing behind`() =
        runTest {
            exportArchiveToFile(target, exporting { it.emit(ArchiveExportProgress.Failed(ExportError.UNKNOWN)) }) {}

            assertEquals(emptyList(), remainingFiles())
        }

    @Test
    fun `progress is passed on as it arrives`() =
        runTest {
            val seen = mutableListOf<Int>()

            exportArchiveToFile(
                target,
                exporting {
                    it.emit(ArchiveExportProgress.InProgress(0.25f, ExportStage.WRITING_ARCHIVE))
                    it.emit(ArchiveExportProgress.Completed(summary))
                },
            ) { seen += (it.fraction * 100).toInt() }

            assertEquals(listOf(25), seen)
        }
}
