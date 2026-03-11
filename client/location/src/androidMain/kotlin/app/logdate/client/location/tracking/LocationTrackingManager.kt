package app.logdate.client.location.tracking

import android.content.Context
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manager for coordinating location tracking based on user settings.
 * This handles starting and stopping scheduled location tracking based on user preferences.
 */
class LocationTrackingManager(
    private val context: Context,
    private val scheduledLocationTrackingService: ScheduledLocationTrackingService,
    private val optimizedBackgroundLocationRegistrar: OptimizedBackgroundLocationRegistrar,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Listen for changes to tracking settings
        scope.launch {
            locationTrackingSettingsRepository.observeSettings().collectLatest { settings ->
                applyTrackingSettings(settings)
            }
        }

        // Apply initial settings
        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            applyTrackingSettings(settings)
        }
    }

    /**
     * Apply tracking settings by starting or stopping tracking services.
     */
    private fun applyTrackingSettings(settings: LocationTrackingSettings) {
        Napier.i("Applying location tracking settings: $settings")

        if (!settings.backgroundTrackingEnabled) {
            scheduledLocationTrackingService.stopScheduledTracking()
            optimizedBackgroundLocationRegistrar.stop()
            context.stopDetailedLocationTrackingService()
            return
        }

        scheduledLocationTrackingService.startScheduledTracking(
            intervalMinutes = settings.minimumPersistIntervalMinutes,
            replaceExisting = true,
        )

        if (settings.captureMode == LocationCaptureMode.EXPERIMENT_MIRRORED) {
            optimizedBackgroundLocationRegistrar.start(settings.minimumPersistIntervalMinutes)
            context.startDetailedLocationTrackingService()
        } else {
            optimizedBackgroundLocationRegistrar.stop()
            context.stopDetailedLocationTrackingService()
        }
    }

    /**
     * Explicitly start location tracking (usually called when the app starts).
     */
    fun startTracking() {
        scope.launch {
            applyTrackingSettings(locationTrackingSettingsRepository.getSettings())
        }
    }

    /**
     * Explicitly stop location tracking (usually called when the app is being destroyed).
     */
    fun stopTracking() {
        scheduledLocationTrackingService.stopScheduledTracking()
        optimizedBackgroundLocationRegistrar.stop()
        context.stopDetailedLocationTrackingService()
    }
}
