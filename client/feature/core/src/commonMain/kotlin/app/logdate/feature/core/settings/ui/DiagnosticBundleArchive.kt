package app.logdate.feature.core.settings.ui

import app.logdate.client.domain.export.ZipArchiveEntry
import app.logdate.client.domain.export.ZipArchiveWriter
import app.logdate.client.sync.diagnostics.DiagnosticReportBundle
import okio.FileSystem
import okio.Path

/** Writes only the prepared, allowlisted local report entries. */
object DiagnosticBundleArchive {
    fun write(
        fileSystem: FileSystem,
        path: Path,
        bundle: DiagnosticReportBundle,
    ) {
        require(bundle.entries.keys == EXPECTED_ENTRIES) { "Unexpected diagnostic report entries" }
        try {
            ZipArchiveWriter(fileSystem).write(
                path,
                bundle.entries.map { (name, content) ->
                    ZipArchiveEntry.Streaming(name) { sink -> sink.writeUtf8(content) }
                },
            )
        } catch (failure: Exception) {
            runCatching { fileSystem.delete(path, mustExist = false) }
            throw failure
        }
    }

    private val EXPECTED_ENTRIES = setOf("summary.md", "report.json", "events.jsonl", "schema.json")
}
