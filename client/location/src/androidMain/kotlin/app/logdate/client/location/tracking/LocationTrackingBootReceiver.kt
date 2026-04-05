package app.logdate.client.location.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import org.koin.mp.KoinPlatformTools

/**
 * Restores location tracking after a device reboot or app update.
 *
 * Responds to [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_MY_PACKAGE_REPLACED] by
 * re-registering location updates through [LocationTrackingManager].
 *
 * @see OptimizedBackgroundLocationRegistrar
 */
class LocationTrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        _context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                Napier.i("Rehydrating location tracking after ${intent.action}")
                val koinContext = KoinPlatformTools.defaultContext().getOrNull()
                if (koinContext == null) {
                    Napier.w("Koin context was not initialized; skipping location boot rehydration.")
                    return
                }
                val locationTrackingManager = koinContext.get<LocationTrackingManager>()
                locationTrackingManager.startTracking()
            }
        }
    }
}
