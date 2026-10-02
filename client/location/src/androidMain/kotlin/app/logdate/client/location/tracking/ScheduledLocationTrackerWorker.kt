package app.logdate.client.location.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.history.LocationTracker
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * WorkManager worker that logs the user's current location at regular intervals.
 */
class ScheduledLocationTrackerWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams),
    KoinComponent {
    private val locationProvider: ClientLocationProvider by inject()
    private val locationTracker: LocationTracker by inject()
    private val settingsRepository: LocationTrackingSettingsRepository by inject()
    private val clock: Clock by inject()

    override suspend fun doWork(): Result {
        Napier.i("ScheduledLocationTrackerWorker: Starting scheduled location tracking")

        try {
            try {
                val settings = settingsRepository.getSettings()
                if (!shouldRecordPeriodicSample(settings, ActivityAwareLocationService.lastFixAt, clock.now())) {
                    Napier.i("ScheduledLocationTrackerWorker: Activity-aware stream is delivering fixes; skipping this sample")
                    return Result.success()
                }
                val fix =
                    withTimeout(30.seconds) {
                        locationProvider.getCurrentFix()
                    }
                val pipeline =
                    if (settings.captureMode == LocationCaptureMode.ACTIVE) {
                        LocationCapturePipeline.OPTIMIZED_BACKGROUND
                    } else {
                        LocationCapturePipeline.LEGACY
                    }

                Napier.i("ScheduledLocationTrackerWorker: Got location accurate to ${fix.accuracyMeters} m")
                locationTracker.logLocation(
                    location = fix.location,
                    timestamp = fix.observedAt,
                    metadata =
                        fix.evidence() +
                            mapOf(
                                "capturePipeline" to pipeline,
                                "captureSource" to LocationCaptureSource.BACKGROUND_PERIODIC,
                            ),
                )
                return Result.success()
            } catch (e: Exception) {
                Napier.w("ScheduledLocationTrackerWorker: Failed to record a periodic location; retrying", e)
                return Result.retry()
            }
        } catch (e: CancellationException) {
            // Don't catch cancellation exceptions
            throw e
        } catch (e: Exception) {
            Napier.e("ScheduledLocationTrackerWorker: Error tracking location", e)
            return Result.failure()
        }
    }
}
