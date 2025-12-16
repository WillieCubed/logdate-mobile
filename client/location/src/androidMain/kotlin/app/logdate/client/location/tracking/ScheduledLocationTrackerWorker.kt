package app.logdate.client.location.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.history.LocationTracker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * WorkManager worker that logs the user's current location at regular intervals.
 */
class ScheduledLocationTrackerWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val locationProvider: ClientLocationProvider by inject()
    private val locationTracker: LocationTracker by inject()

    override suspend fun doWork(): Result {
        Napier.i("ScheduledLocationTrackerWorker: Starting scheduled location tracking")
        
        try {
            // Request a fresh location update
            locationProvider.refreshLocation()
            
            // Try to get current location with timeout
            try {
                val location = withTimeout(30.seconds) {
                    locationProvider.getCurrentLocation()
                }
                
                Napier.i("ScheduledLocationTrackerWorker: Got location: $location")
                locationTracker.logLocation(location)
                return Result.success()
            } catch (e: Exception) {
                Napier.w("ScheduledLocationTrackerWorker: Failed to get location within timeout", e)
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
