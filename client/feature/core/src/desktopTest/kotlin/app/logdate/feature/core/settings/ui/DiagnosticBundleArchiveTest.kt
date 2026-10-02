package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.diagnostics.DiagnosticReportBundles
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class DiagnosticBundleArchiveTest {
    @Test
    fun `archive contains exactly the prepared preview and report entries`() {
        val bundle = DiagnosticReportBundles.prepare(SyncDiagnosticReport(Uuid.random().toString()))
        val file = Files.createTempFile("logdate-diagnostic-test-", ".zip").toFile()
        try {
            DiagnosticBundleArchive.write(FileSystem.SYSTEM, file.absolutePath.toPath(), bundle)
            ZipFile(file).use { zip ->
                val names =
                    zip
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toSet()
                assertEquals(bundle.entries.keys, names)
                bundle.entries.forEach { (name, content) ->
                    assertEquals(content, zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() })
                }
                assertEquals(bundle.summary, bundle.entries.getValue("summary.md"))
            }
        } finally {
            file.delete()
        }
    }
}
