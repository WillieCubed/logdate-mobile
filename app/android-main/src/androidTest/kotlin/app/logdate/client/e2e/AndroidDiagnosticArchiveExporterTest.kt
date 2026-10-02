package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.diagnostics.DiagnosticReportBundles
import app.logdate.feature.core.settings.ui.AndroidDiagnosticArchiveExporter
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AndroidDiagnosticArchiveExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bundle = DiagnosticReportBundles.prepare(SyncDiagnosticReport(Uuid.random().toString()))

    @Test
    fun `cancellation during expiry scheduling propagates and deletes private zip`() = runTest {
        var archive: File? = null
        val exporter = AndroidDiagnosticArchiveExporter(
            context,
            enqueueCleanup = { file ->
                archive = file
                assertTrue(file.isFile)
                throw CancellationException("cancelled while enqueueing")
            },
            launchShare = { error("chooser must not open") },
        )

        assertFailsWith<CancellationException> { exporter.export(bundle) }
        assertFalse(archive?.exists() == true)
    }

    @Test
    fun `cancellation before chooser launch propagates and deletes private zip`() = runTest {
        var archive: File? = null
        val exporter = AndroidDiagnosticArchiveExporter(
            context,
            enqueueCleanup = { true },
            launchShare = { file ->
                archive = file
                throw CancellationException("cancelled before share")
            },
        )

        assertFailsWith<CancellationException> { exporter.export(bundle) }
        assertFalse(archive?.exists() == true)
    }

    @Test
    fun `successful share keeps private zip only after expiry has been scheduled`() = runTest {
        var scheduled: File? = null
        var shared: File? = null
        val exporter = AndroidDiagnosticArchiveExporter(
            context,
            enqueueCleanup = { file -> scheduled = file; true },
            launchShare = { file -> shared = file; true },
        )
        try {
            assertTrue(exporter.export(bundle))
            assertTrue(scheduled === shared)
            assertTrue(shared?.isFile == true)
        } finally {
            shared?.delete()
        }
    }
}
