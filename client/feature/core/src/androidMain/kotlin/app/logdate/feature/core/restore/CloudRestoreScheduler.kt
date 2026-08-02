package app.logdate.feature.core.restore

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aakira.napier.Napier

/** Schedules an authenticated cloud restore without affecting local/offline data. */
class CloudRestoreScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueRestore() {
        val request =
            OneTimeWorkRequestBuilder<CloudRestoreWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        workManager.enqueueUniqueWork(
            CloudRestoreWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        Napier.d("Enqueued LogDate Cloud restore")
    }
}
