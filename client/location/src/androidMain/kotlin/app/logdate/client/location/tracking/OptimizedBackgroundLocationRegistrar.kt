package app.logdate.client.location.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.logdate.client.location.ClientLocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.minutes

internal const val OPTIMIZED_BACKGROUND_LOCATION_UPDATE_ACTION =
    "app.logdate.location.action.OPTIMIZED_BACKGROUND_LOCATION_UPDATE"

/**
 * Manages registration of passive background location updates with Google Play Services.
 *
 * "Passive" means the device only delivers a location fix when some other app has already
 * requested one, avoiding extra battery drain. When a fix arrives, it is broadcast to
 * [OptimizedBackgroundLocationReceiver] for persistence.
 *
 * @see OptimizedBackgroundLocationReceiver
 * @see LocationTrackingBootReceiver
 */
class OptimizedBackgroundLocationRegistrar(
    private val context: Context,
    private val locationProvider: ClientLocationProvider,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
) {
    companion object {
        /** Minimum allowed interval between passive updates, in minutes. */
        private const val MIN_INTERVAL_MINUTES = 2L

        /** Minimum distance the device must move before an update is delivered, in meters. */
        private const val MIN_UPDATE_DISTANCE_METERS = 50f

        /** Updates may be batched for up to this multiple of the requested interval. */
        private const val MAX_DELAY_MULTIPLIER = 3
    }

    /**
     * Registers passive location updates with the given [minimumPersistIntervalMinutes] interval.
     *
     * The interval is clamped to a minimum of [MIN_INTERVAL_MINUTES] minutes. Updates are also
     * suppressed when the device has moved less than [MIN_UPDATE_DISTANCE_METERS] meters, and
     * may be batched for up to [MAX_DELAY_MULTIPLIER]x the interval to save battery. No-ops if
     * location permission has not been granted.
     */
    fun start(minimumPersistIntervalMinutes: Long) {
        if (!locationProvider.hasLocationPermission()) {
            Napier.w("Skipping optimized background location registration because location permission is missing")
            return
        }

        val intervalMillis = minimumPersistIntervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).minutes.inWholeMilliseconds
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_PASSIVE, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
                .setMaxUpdateDelayMillis(intervalMillis * MAX_DELAY_MULTIPLIER)
                .build()

        try {
            fusedLocationClient
                .requestLocationUpdates(request, optimizedBackgroundPendingIntent(context))
                .addOnSuccessListener {
                    Napier.i("Registered passive background location updates every ~$minimumPersistIntervalMinutes minutes")
                }.addOnFailureListener { error ->
                    Napier.w("Failed to register passive background location updates", error)
                }
        } catch (error: SecurityException) {
            Napier.w("Failed to register passive background location updates", error)
        }
    }

    /** Unregisters passive location updates. */
    fun stop() {
        try {
            fusedLocationClient
                .removeLocationUpdates(optimizedBackgroundPendingIntent(context))
                .addOnSuccessListener {
                    Napier.i("Removed passive background location updates")
                }.addOnFailureListener { error ->
                    Napier.w("Failed to remove passive background location updates", error)
                }
        } catch (error: SecurityException) {
            Napier.w("Failed to remove passive background location updates", error)
        }
    }
}

internal fun optimizedBackgroundPendingIntent(context: Context): PendingIntent {
    val flags =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

    return PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, OptimizedBackgroundLocationReceiver::class.java).apply {
            action = OPTIMIZED_BACKGROUND_LOCATION_UPDATE_ACTION
        },
        flags,
    )
}
