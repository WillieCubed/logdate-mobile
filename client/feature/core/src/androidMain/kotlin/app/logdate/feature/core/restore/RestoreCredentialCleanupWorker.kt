package app.logdate.feature.core.restore

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import app.logdate.client.data.account.RestoreCredentialCleanupScheduler
import app.logdate.client.data.account.RestoreCredentialLifecycle
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RestoreCredentialCleanupWorker(
    context: Context,
    params: WorkerParameters,
    private val lifecycle: RestoreCredentialLifecycle,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ticketId = inputData.getString(TICKET_ID) ?: return Result.success()
        return try {
            if (lifecycle.cleanup(ticketId)) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Restore credential cleanup will retry", error)
            Result.retry()
        }
    }

    companion object {
        const val TICKET_ID = "restore_cleanup_ticket"
    }
}

class AndroidRestoreCredentialCleanupScheduler(
    private val context: Context,
) : RestoreCredentialCleanupScheduler {
    override suspend fun enqueue(ticketId: String) {
        val request =
            OneTimeWorkRequestBuilder<RestoreCredentialCleanupWorker>()
                .setInputData(workDataOf(RestoreCredentialCleanupWorker.TICKET_ID to ticketId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork("restore-credential-cleanup-$ticketId", ExistingWorkPolicy.KEEP, request)
            .await()
    }
}
