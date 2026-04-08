package app.logdate.client.events

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.events.EventInferenceSensitivity
import app.logdate.client.domain.events.InferEventsUseCase
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * Background worker that runs the on-device event inference pipeline.
 *
 * The worker is gated on the user's master "auto-events" toggle, reads sensitivity and
 * naming preferences off the same preferences store, then dispatches into [InferEventsUseCase].
 * On every run — success or failure — it writes a fresh stats record so the auto-events
 * settings screen can show "last run", "events created", and "last error" without standing
 * up a separate repository.
 */
class EventInferenceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val inferEvents: InferEventsUseCase by inject()
    private val preferences: LogdatePreferencesDataSource by inject()

    override suspend fun doWork(): Result {
        Napier.d(tag = TAG, message = "Starting event inference pass")

        if (!preferences.isEventsEnabled()) {
            Napier.d(tag = TAG, message = "Events feature disabled, skipping run")
            return Result.success()
        }

        val sensitivity = EventInferenceSensitivity.fromPreference(preferences.getEventInferenceSensitivity())
        val aiNamingEnabled = preferences.isEventInferenceAiNamingEnabled()

        val outcome = inferEvents(sensitivity = sensitivity, aiNamingEnabled = aiNamingEnabled)

        return outcome.fold(
            onSuccess = { createdCount ->
                Napier.d(tag = TAG, message = "Event inference pass created $createdCount events")
                preferences.recordEventInferenceRun(
                    runAt = Clock.System.now(),
                    createdThisRun = createdCount,
                    error = null,
                )
                Result.success()
            },
            onFailure = { throwable ->
                Napier.w(tag = TAG, message = "Event inference pass failed", throwable = throwable)
                preferences.recordEventInferenceRun(
                    runAt = Clock.System.now(),
                    createdThisRun = 0,
                    error = throwable.message ?: throwable::class.simpleName ?: "unknown error",
                )
                Result.retry()
            },
        )
    }

    companion object {
        const val WORK_NAME = "logdate:events:inference"
        private const val TAG = "EventInferenceWorker"
    }
}
