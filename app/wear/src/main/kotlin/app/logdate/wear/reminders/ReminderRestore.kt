package app.logdate.wear.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * After a restart or an update, asks WorkManager to restore reminders: Health Services can take 10
 * seconds or more to accept a registration, longer than a receiver may run.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                ReminderRestoreWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRestoreWorker>().build(),
            )
    }
}

/** Restores what reminders need, while they are on. */
class ReminderRestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val reminders: WatchReminders by inject()

    override suspend fun doWork(): Result =
        try {
            reminders.restore()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Restoring reminders after a restart failed", e)
            Result.retry()
        }

    companion object {
        const val UNIQUE_WORK_NAME = "reminder_restore"
    }
}
