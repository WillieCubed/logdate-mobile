package app.logdate.client.calendar

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic and on-demand runs of the device calendar import worker.
 *
 * The periodic schedule runs every twelve hours — slower than the auto-events inference
 * worker because the user's calendar doesn't churn that fast and a fresher cadence would
 * waste battery on duplicate reads. The settings overview exposes a "Sync now" button
 * that calls into [enqueueImmediateRun] for the rare case where the user just added or
 * edited an event on another device and wants it mirrored immediately.
 *
 * The periodic job is gated on `setRequiresBatteryNotLow` so a six-hour content-provider
 * walk doesn't fire during the user's last red-bar minutes. Network and idle constraints
 * are intentionally omitted — this worker reads local content providers, not the network,
 * and idle would stretch the cadence into days.
 */
class CalendarImportScheduler(
    private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicImport() {
        val constraints =
            Constraints
                .Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        val periodicRequest =
            PeriodicWorkRequestBuilder<CalendarImportWorker>(
                IMPORT_PERIOD_HOURS,
                TimeUnit.HOURS,
            ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            CalendarImportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        Napier.d("Scheduled periodic device calendar import")
    }

    fun enqueueImmediateRun() {
        val request =
            OneTimeWorkRequestBuilder<CalendarImportWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        workManager.enqueueUniqueWork(
            "${CalendarImportWorker.WORK_NAME}:immediate",
            ExistingWorkPolicy.KEEP,
            request,
        )

        Napier.d("Enqueued immediate device calendar import")
    }

    companion object {
        private const val IMPORT_PERIOD_HOURS = 12L
    }
}
