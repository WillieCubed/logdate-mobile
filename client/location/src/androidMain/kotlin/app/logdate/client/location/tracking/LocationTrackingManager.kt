package app.logdate.client.location.tracking

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
    private val scheduledLocationTrackingService: ScheduledLocationTrackingService,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository
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
        
        if (settings.backgroundTrackingEnabled) {
            scheduledLocationTrackingService.startScheduledTracking(
                intervalMinutes = settings.trackingIntervalMinutes,
                replaceExisting = true
            )
        } else {
            scheduledLocationTrackingService.stopScheduledTracking()
        }
    }
    
    /**
     * Explicitly start location tracking (usually called when the app starts).
     */
    fun startTracking() {
        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            if (settings.backgroundTrackingEnabled) {
                scheduledLocationTrackingService.startScheduledTracking(
                    intervalMinutes = settings.trackingIntervalMinutes,
                    replaceExisting = false
                )
            }
        }
    }
    
    /**
     * Explicitly stop location tracking (usually called when the app is being destroyed).
     */
    fun stopTracking() {
        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            if (!settings.backgroundTrackingEnabled) {
                scheduledLocationTrackingService.stopScheduledTracking()
            }
        }
    }
}