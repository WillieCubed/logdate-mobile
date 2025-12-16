package app.logdate.client.location.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import java.util.concurrent.TimeUnit

/**
 * Service that schedules periodic location tracking using WorkManager.
 */
class ScheduledLocationTrackingService(
    private val context: Context
) {
    companion object {
        private const val WORK_NAME = "scheduled_location_tracking"
        
        // Default tracking interval: 30 minutes
        private const val DEFAULT_INTERVAL_MINUTES = 30L
    }
    
    /**
     * Start scheduled location tracking with the specified interval.
     * 
     * @param intervalMinutes How often to track location, in minutes. Must be at least 15 minutes
     *                        due to WorkManager constraints.
     * @param replaceExisting Whether to replace an existing scheduled work if one exists.
     */
    fun startScheduledTracking(
        intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
        replaceExisting: Boolean = true
    ) {
        // WorkManager requires a minimum interval of 15 minutes
        val actualInterval = intervalMinutes.coerceAtLeast(15)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<ScheduledLocationTrackerWorker>(
            actualInterval, TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        
        val policy = if (replaceExisting) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            workRequest
        )
        
        Napier.i("ScheduledLocationTrackingService: Started tracking location every $actualInterval minutes")
    }
    
    /**
     * Stop scheduled location tracking.
     */
    fun stopScheduledTracking() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Napier.i("ScheduledLocationTrackingService: Stopped scheduled location tracking")
    }
}