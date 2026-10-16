package app.logdate.feature.core.restore

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent

/** Keeps accepted entries usable while their originals recover independently. */
class ArchiveMediaRecoveryWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    override suspend fun doWork(): Result {
        val id = inputData.getString(RestoreWorker.RECOVERY_ID_KEY) ?: return Result.success()
        return ArchiveRecoveryLocks.withLock(id) {
            try {
                val record = context.archiveRecoveryStore().read(id) ?: return@withLock Result.success()
                if (record.pausedByUser ||
                    record.scope != currentArchiveRecoveryScope(getKoin()) ||
                    !record.metadataApplied ||
                    !record.options.includeMedia
                ) {
                    return@withLock Result.success()
                }
                try {
                    setForeground(
                        RestoreNotificationHelper(
                            context,
                            kotlin.uuid.Uuid.parse(this.id.toString()),
                        ).createForegroundInfo(RestoreStage.IMPORTING_MEDIA),
                    )
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    Napier.w("Media recovery continues without a foreground notification")
                }
                val summary = ArchiveMediaRecoveryEngine(context, getKoin()).repair(record)
                getKoin().get<RestoreLauncher>().updateRecovery(summary)
                if (summary.unavailableOriginals + summary.unavailablePreviews == 0) Result.success() else Result.retry()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Napier.w("Attachment recovery retained its checkpoint and will retry")
                Result.retry()
            }
        }
    }
}

internal object ArchiveRecoveryScheduler {
    suspend fun enqueueAll(
        context: Context,
        koin: org.koin.core.Koin,
    ) {
        val scope = currentArchiveRecoveryScope(koin)
        val records = context.archiveRecoveryStore().pending(scope)
        if (currentArchiveRecoveryScope(koin) != scope) return
        records.forEach { enqueue(context, it) }
    }

    fun workName(
        id: String,
        media: Boolean,
    ) = "archive_${if (media) "media" else "metadata"}_recovery_$id"

    fun cancel(
        context: Context,
        id: String,
    ) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(workName(id, false))
        manager.cancelUniqueWork(workName(id, true))
        manager.cancelUniqueWork("archive_recovery_$id")
    }

    fun enqueue(
        context: Context,
        record: ArchiveRecoveryRecord,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        if (record.pausedByUser ||
            record.requiresSource &&
            !record.metadataApplied ||
            record.metadataApplied &&
            !record.options.includeMedia
        ) {
            return
        }
        val input =
            workDataOf(
                RestoreWorker.RECOVERY_ID_KEY to record.id,
                RestoreWorker.SILENT_RECOVERY_KEY to true,
                RestoreWorker.EXPECTED_OWNER_KEY to record.scope.ownerId,
                RestoreWorker.EXPECTED_ORIGIN_KEY to record.scope.serverOrigin,
                RestoreWorker.INCLUDE_DRAFTS_KEY to record.options.includeDrafts,
                RestoreWorker.INCLUDE_MEDIA_KEY to record.options.includeMedia,
                RestoreWorker.PRESERVE_POPULATED_PROFILE_KEY to true,
            )
        val work =
            if (record.metadataApplied) {
                OneTimeWorkRequestBuilder<ArchiveMediaRecoveryWorker>().setInputData(input).build()
            } else {
                OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(input).build()
            }
        WorkManager.getInstance(context).enqueueUniqueWork(workName(record.id, record.metadataApplied), policy, work)
    }
}
