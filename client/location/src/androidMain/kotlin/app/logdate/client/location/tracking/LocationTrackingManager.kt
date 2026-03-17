package app.logdate.client.location.tracking

import android.content.Context
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Manager for coordinating location tracking based on user settings.
 *
 * Observes setting changes and starts/stops the appropriate tracking services:
 * - [ScheduledLocationTrackingService] (WorkManager periodic) — always active when tracking is on
 * - [OptimizedBackgroundLocationRegistrar] (passive FLP) — active in ACTIVE mode as a fallback
 * - [ActivityAwareLocationService] (foreground service) — active in ACTIVE mode for high-accuracy,
 *   activity-adaptive tracking
 */
@OptIn(FlowPreview::class)
class LocationTrackingManager(
    private val context: Context,
    private val scheduledLocationTrackingService: ScheduledLocationTrackingService,
    private val optimizedBackgroundLocationRegistrar: OptimizedBackgroundLocationRegistrar,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var latestSettings: LocationTrackingSettings? = null

    init {
        scope.launch {
            locationTrackingSettingsRepository.observeSettings().debounce(1000).collectLatest { settings ->
                latestSettings = settings
                applyTrackingSettings(settings)
            }
        }

        scope.launch {
            val settings = locationTrackingSettingsRepository.getSettings()
            latestSettings = settings
            applyTrackingSettings(settings)
        }
    }

    private fun applyTrackingSettings(settings: LocationTrackingSettings) {
        Napier.i("Applying location tracking settings: $settings")
        val decision = computeLocationTrackingExecutionDecision(settings = settings)

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

        if (decision.shouldStartActivityAwareTracking) {
            context.startActivityAwareLocationService(permissionManager)
        }

        if (decision.shouldStopActivityAwareTracking) {
            context.stopActivityAwareLocationService()
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

    /** No-op — activity-aware tracking no longer depends on foreground activity state. */
    fun onActivityResumed() = Unit

    /** No-op — activity-aware tracking no longer depends on foreground activity state. */
    fun onActivityPaused() = Unit

    /**
     * Explicitly stop location tracking (usually called when the app is being destroyed).
     */
    fun stopTracking() {
        scheduledLocationTrackingService.stopScheduledTracking()
        optimizedBackgroundLocationRegistrar.stop()
        context.stopActivityAwareLocationService()
    }
}
