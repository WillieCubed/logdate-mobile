package app.logdate.feature.core.restore

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudBackupDataSource
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Downloads the newest authenticated Cloud backup and hands it to the normal restore pipeline.
 *
 * The worker never mutates the local database itself. [RestoreWorker] remains the single archive
 * applier, which preserves its merge semantics, media import behavior, progress reporting, and
 * one-owner safeguards.
 */
class CloudRestoreWorker(
    private val context: Context,
    params: WorkerParameters,
    private val cloudBackupDataSource: CloudBackupDataSource,
    private val sessionStorage: SessionStorage,
    private val enqueueRestore: (File) -> Unit = { archive ->
        val restoreRequest =
            OneTimeWorkRequestBuilder<RestoreWorker>()
                .setInputData(
                    workDataOf(
                        RestoreWorker.SOURCE_URI_KEY to Uri.fromFile(archive).toString(),
                        RestoreWorker.DELETE_SOURCE_AFTER_RESTORE_KEY to true,
                        RestoreWorker.INCLUDE_DRAFTS_KEY to true,
                        RestoreWorker.INCLUDE_MEDIA_KEY to true,
                    ),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RestoreWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            restoreRequest,
        )
    },
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = sessionStorage.getSession() ?: return Result.success()
        return try {
            val backup =
                cloudBackupDataSource
                    .listBackups(session.accessToken)
                    .getOrElse { error ->
                        Napier.w("CloudRestoreWorker: unable to list backups", error)
                        return Result.retry()
                    }.maxByOrNull { it.createdAt }
                    ?: return Result.success()

            val archive = File(context.filesDir, "cloud-restore-$id.zip")
            val downloaded =
                cloudBackupDataSource
                    .downloadBackup(session.accessToken, backup.id)
                    .getOrElse { error ->
                        Napier.w("CloudRestoreWorker: unable to download backup ${backup.id}", error)
                        return Result.retry()
                    }
            archive.writeBytes(downloaded.data)

            enqueueRestore(archive)
            Result.success()
        } catch (error: Throwable) {
            Napier.w("CloudRestoreWorker: restore handoff failed", error)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "logdate:cloud:restore"
    }
}
