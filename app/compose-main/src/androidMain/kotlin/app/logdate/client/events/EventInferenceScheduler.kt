package app.logdate.client.events

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
 * Schedules periodic and on-demand runs of the event inference worker.
 *
 * The periodic schedule runs every six hours so newly captured photos and notes have a
 * chance to land in an event soon after they happen, without burning battery on an aggressive
 * cadence. Settings exposes a "run now" button that calls into [enqueueImmediateRun].
 */
class EventInferenceScheduler(
    private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedules the periodic event inference job. Idempotent — calling this on every app
     * startup is safe and will not duplicate the work.
     *
     * The job runs only when the device isn't on a critically low battery: every six hours
     * the worker reads several Room tables, hits DataStore, and may make a network call for
     * AI naming, so we don't want it firing during the user's last red-bar minutes. Idle and
     * network constraints are intentionally omitted — idle would stretch the cadence into
     * days, and network gating would defeat the offline heuristic-naming fallback.
     */
    fun schedulePeriodicInference() {
        val constraints =
            Constraints
                .Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        val periodicRequest =
            PeriodicWorkRequestBuilder<EventInferenceWorker>(
                INFERENCE_PERIOD_HOURS,
                TimeUnit.HOURS,
            ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            EventInferenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        Napier.d("Scheduled periodic event inference")
    }

    /**
     * Triggers an immediate run of the event inference worker. Used by the auto-events
     * settings screen's "Run now" button.
     */
    fun enqueueImmediateRun() {
        val request =
            OneTimeWorkRequestBuilder<EventInferenceWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        workManager.enqueueUniqueWork(
            "${EventInferenceWorker.WORK_NAME}:immediate",
            ExistingWorkPolicy.KEEP,
            request,
        )

        Napier.d("Enqueued immediate event inference run")
    }

    companion object {
        private const val INFERENCE_PERIOD_HOURS = 6L
    }
}
