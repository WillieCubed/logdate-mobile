package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.diagnostics.DiagnosticReportBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

/** Writes privately in the chosen directory, then publishes only the completed ZIP. */
class DesktopDiagnosticArchiveExporter : DiagnosticArchiveExporter {
    override suspend fun export(bundle: DiagnosticReportBundle): Boolean =
        withContext(Dispatchers.IO) {
            val dialog =
                FileDialog(null as Frame?, "Save sync diagnostic report", FileDialog.SAVE).apply {
                    file = "logdate-sync-diagnostics.zip"
                    isVisible = true
                }
            val fileName = dialog.file ?: return@withContext false
            val directory = dialog.directory ?: return@withContext false
            val selected = File(directory, if (fileName.endsWith(".zip", ignoreCase = true)) fileName else "$fileName.zip")
            val temp =
                try {
                    Files.createTempFile(selected.parentFile.toPath(), ".logdate-diagnostic-", ".tmp")
                } catch (_: Exception) {
                    return@withContext false
                }
            try {
                DiagnosticBundleArchive.write(FileSystem.SYSTEM, temp.toString().toPath(), bundle)
                coroutineContext.ensureActive()
                try {
                    Files.move(temp, selected.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, selected.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            } finally {
                Files.deleteIfExists(temp)
            }
        }
}
