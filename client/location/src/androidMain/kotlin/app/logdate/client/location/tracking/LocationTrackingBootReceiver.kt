package app.logdate.client.location.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restores location tracking after a device reboot or app update.
 *
 * Responds to [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_MY_PACKAGE_REPLACED] by
 * re-registering location updates through [LocationTrackingManager].
 *
 * @see OptimizedBackgroundLocationRegistrar
 */
class LocationTrackingBootReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val locationTrackingManager: LocationTrackingManager by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                Napier.i("Rehydrating location tracking after ${intent.action}")
                locationTrackingManager.startTracking()
            }
        }
    }
}
