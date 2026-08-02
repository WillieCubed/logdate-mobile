package app.logdate.feature.core.export

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import java.util.concurrent.TimeUnit

/** Schedules authenticated cloud backups without affecting offline-first local writes. */
class CloudBackupScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicBackup() {
        val request =
            PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(CloudBackupWorker.NETWORK_CONSTRAINTS)
                .build()
        workManager.enqueueUniquePeriodicWork(
            CloudBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Napier.d("Scheduled periodic LogDate Cloud backup")
    }

    fun enqueueImmediateBackup() {
        val request =
            OneTimeWorkRequestBuilder<CloudBackupWorker>()
                .setConstraints(CloudBackupWorker.NETWORK_CONSTRAINTS)
                .build()
        workManager.enqueueUniqueWork(
            "${CloudBackupWorker.WORK_NAME}:immediate",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Napier.d("Enqueued immediate LogDate Cloud backup")
    }
}
