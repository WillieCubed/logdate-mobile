package app.logdate.client.watch.presence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WatchCatchUpWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val catchUp: WatchCatchUp by inject()

    override suspend fun doWork(): Result =
        try {
            if (catchUp.run()) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Watch presence catch-up will retry", e)
            Result.retry()
        }

    companion object {
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<WatchCatchUpWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork("watch_presence_catch_up", ExistingWorkPolicy.KEEP, request)
        }
    }
}
