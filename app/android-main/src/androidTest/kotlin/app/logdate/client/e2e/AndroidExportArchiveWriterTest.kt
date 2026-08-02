package app.logdate.client.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportResult
import app.logdate.feature.core.export.AndroidExportArchiveWriter
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidExportArchiveWriterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun writesExportResultToAnAppPrivateZipWithMedia() {
        val mediaSource = File.createTempFile("logdate_archive_media", ".txt", context.cacheDir)
        mediaSource.writeText("private media bytes")
        val outputName = "archive-writer-test-${System.nanoTime()}.zip"
        val result = mockk<ExportResult>(relaxed = true)
        every { result.mediaFiles } returns listOf(ExportMediaFile("media/sample.txt", mediaSource.absolutePath))

        try {
            val archive = AndroidExportArchiveWriter(context).writeToAppPrivateFile(result, outputName)

            assertEquals(context.filesDir, archive.parentFile)
            ZipFile(archive).use { zip ->
                assertTrue(zip.getEntry("metadata.json") != null)
                assertTrue(zip.getEntry("journals.json") != null)
                assertTrue(zip.getEntry("notes.json") != null)
                assertTrue(zip.getEntry("journal_notes.json") != null)
                assertTrue(zip.getEntry("drafts.json") != null)
                assertTrue(zip.getEntry("media/sample.txt") != null)
                assertContentEquals(
                    "private media bytes".encodeToByteArray(),
                    zip.getInputStream(zip.getEntry("media/sample.txt")).use { it.readBytes() },
                )
            }
        } finally {
            mediaSource.delete()
            File(context.filesDir, outputName).delete()
        }
    }
}
