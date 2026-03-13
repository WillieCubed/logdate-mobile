package app.logdate.client.location.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.logdate.client.location.ClientLocationProvider
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.minutes

internal const val OPTIMIZED_BACKGROUND_LOCATION_UPDATE_ACTION =
    "app.logdate.location.action.OPTIMIZED_BACKGROUND_LOCATION_UPDATE"

class OptimizedBackgroundLocationRegistrar(
    private val context: Context,
    private val locationProvider: ClientLocationProvider,
) {
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun start(minimumPersistIntervalMinutes: Long) {
        if (!locationProvider.hasLocationPermission()) {
            Napier.w("Skipping optimized background location registration because location permission is missing")
            return
        }

        val intervalMillis = minimumPersistIntervalMinutes.coerceAtLeast(2).minutes.inWholeMilliseconds
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_PASSIVE, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .setMinUpdateDistanceMeters(50f)
                .setMaxUpdateDelayMillis(intervalMillis * 3)
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
