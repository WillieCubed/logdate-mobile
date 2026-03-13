package app.logdate.client.location.tracking

import android.content.Context
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
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
    private val permissionManager: PermissionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val foregroundActivityCounter = ForegroundActivityCounter()
    private var latestSettings: LocationTrackingSettings? = null

    init {
        // Listen for changes to tracking settings
        scope.launch {
            locationTrackingSettingsRepository.observeSettings().collectLatest { settings ->
                latestSettings = settings
                applyTrackingSettings(settings)
            }
        }

        // Apply initial settings
        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            latestSettings = settings
            applyTrackingSettings(settings)
        }
    }

    /**
     * Apply tracking settings by starting or stopping tracking services.
     */
    private fun applyTrackingSettings(settings: LocationTrackingSettings) {
        Napier.i("Applying location tracking settings: $settings")
        val canStartDetailedForegroundTracking = foregroundActivityCounter.hasForegroundActivities()
        val decision =
            computeLocationTrackingExecutionDecision(
                settings = settings,
                canStartDetailedForegroundTracking = canStartDetailedForegroundTracking,
            )

        if (decision.shouldStopScheduledTracking) {
            scheduledLocationTrackingService.stopScheduledTracking()
        }

        if (decision.shouldStartScheduledTracking) {
            scheduledLocationTrackingService.startScheduledTracking(
                intervalMinutes = settings.minimumPersistIntervalMinutes,
                replaceExisting = true,
            )
        }

        if (decision.shouldStartOptimizedBackgroundTracking) {
            optimizedBackgroundLocationRegistrar.start(settings.minimumPersistIntervalMinutes)
        }

        if (decision.shouldStopOptimizedBackgroundTracking) {
            optimizedBackgroundLocationRegistrar.stop()
        }

        if (decision.shouldStartDetailedForegroundTracking) {
            context.startDetailedLocationTrackingService(permissionManager)
        } else if (
            settings.backgroundTrackingEnabled &&
            settings.captureMode == LocationCaptureMode.EXPERIMENT_MIRRORED &&
            !canStartDetailedForegroundTracking
        ) {
            Napier.i("Deferring detailed location tracking until an activity is resumed")
        }

        if (decision.shouldStopDetailedForegroundTracking) {
            context.stopDetailedLocationTrackingService()
        }
    }

    /**
     * Explicitly start location tracking (usually called when the app starts).
     */
    fun startTracking() {
        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            latestSettings = settings
            applyTrackingSettings(settings)
        }
    }

    /**
     * Notifies the manager that an app activity is now resumed and foreground-visible.
     */
    fun onActivityResumed() {
        val transitionedToForeground = foregroundActivityCounter.onActivityResumed()
        if (!transitionedToForeground) {
            return
        }

        scope.launch {
            val settings = latestSettings ?: locationTrackingSettingsRepository.getSettings()
            latestSettings = settings
            applyTrackingSettings(settings)
        }
    }

    /**
     * Notifies the manager that an app activity is paused.
     */
    fun onActivityPaused() {
        foregroundActivityCounter.onActivityPaused()
    }

    /**
     * Explicitly stop location tracking (usually called when the app is being destroyed).
     */
    fun stopTracking() {
        foregroundActivityCounter.reset()
        scheduledLocationTrackingService.stopScheduledTracking()
        optimizedBackgroundLocationRegistrar.stop()
        context.stopDetailedLocationTrackingService()
    }
}
