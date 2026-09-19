package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipOutputStream

internal sealed interface ArchiveFileOutcome {
    data class Completed(
        val summary: ArchiveExportSummary,
    ) : ArchiveFileOutcome

    data class Failed(
        val message: String,
    ) : ArchiveFileOutcome
}

/**
 * Writes an export archive to [target] by way of a temporary file beside it.
 *
 * [target] is only replaced once the archive is complete, so a failed or cancelled export never
 * damages a file the person chose to overwrite. The temporary file is removed on every other path.
 * Errors, including cancellation, are passed on after the cleanup.
 */
internal suspend fun exportArchiveToFile(
    target: File,
    export: (ArchiveContainer) -> Flow<ArchiveExportProgress>,
    onProgress: (ArchiveExportProgress.InProgress) -> Unit,
): ArchiveFileOutcome {
    val partial = File(target.absoluteFile.parentFile, "${target.name}.part")
    var replaced = false
    try {
        var summary: ArchiveExportSummary? = null
        var failure: String? = null
        ZipOutputStream(FileOutputStream(partial).buffered()).use { zip ->
            export(ZipStreamArchiveContainer(zip)).collect { progress ->
                when (progress) {
                    ArchiveExportProgress.Starting -> Napier.i("Desktop: Archive export started")
                    is ArchiveExportProgress.InProgress -> onProgress(progress)
                    is ArchiveExportProgress.Completed -> summary = progress.summary
                    is ArchiveExportProgress.Failed -> failure = progress.error.defaultMessage
                }
            }
        }
        val completed = summary
        if (failure != null || completed == null) return ArchiveFileOutcome.Failed(failure ?: "Export could not be completed.")

        moveOver(partial, target)
        replaced = true
        return ArchiveFileOutcome.Completed(completed)
    } finally {
        if (!replaced) partial.delete()
    }
}

private fun moveOver(
    partial: File,
    target: File,
) {
    try {
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (unsupported: AtomicMoveNotSupportedException) {
        Napier.w("Desktop: Atomic move is not supported here, replacing the file directly", unsupported)
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
