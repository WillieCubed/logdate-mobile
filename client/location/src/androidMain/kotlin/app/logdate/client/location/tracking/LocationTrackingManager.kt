package app.logdate.client.location.tracking

import android.content.Context
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private var appliedSettings: LocationTrackingSettings? = null
    private var locationPermissionGranted: Boolean? = null

    init {
        scope.launch {
            locationTrackingSettingsRepository.observeSettings().debounce(1000).collectLatest {
                reconcileTrackingSettings()
            }
        }

        scope.launch {
            reconcileTrackingSettings()
        }
    }

    private fun applyTrackingSettings(settings: LocationTrackingSettings) {
        val granted = permissionManager.isPermissionGranted(PermissionType.LOCATION)
        if (appliedSettings == settings && locationPermissionGranted == granted) return
        Napier.i("Applying location tracking settings: $settings")
        val decision =
            computeLocationTrackingExecutionDecision(
                settings = settings.copy(backgroundTrackingEnabled = settings.backgroundTrackingEnabled && granted),
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

        if (decision.shouldStartActivityAwareTracking) {
            context.startActivityAwareLocationService(permissionManager)
        }

        if (decision.shouldStopActivityAwareTracking) {
            context.stopActivityAwareLocationService()
        }
        appliedSettings = settings
        locationPermissionGranted = granted
    }

    /**
     * Explicitly start location tracking (usually called when the app starts).
     */
    fun startTracking() {
        scope.launch {
            reconcileTrackingSettings()
        }
    }

    /** Reconciles grants changed outside the app while retaining the user's tracking preference. */
    fun onActivityResumed() {
        scope.launch { reconcileTrackingSettings(retryForeground = true) }
    }

    private suspend fun reconcileTrackingSettings(retryForeground: Boolean = false) {
        try {
            val settings = locationTrackingSettingsRepository.getSettings()
            val granted = permissionManager.isPermissionGranted(PermissionType.LOCATION)
            val unchanged = appliedSettings == settings && locationPermissionGranted == granted
            applyTrackingSettings(settings)
            if (retryForeground &&
                unchanged &&
                granted &&
                settings.backgroundTrackingEnabled &&
                settings.captureMode == LocationCaptureMode.ACTIVE
            ) {
                // A background launch may have been denied before the activity became visible.
                context.startActivityAwareLocationService(permissionManager)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Location tracking reconciliation will retry when the app resumes", error)
        }
    }

    /** No-op — activity-aware tracking no longer depends on foreground activity state. */
    fun onActivityPaused() = Unit

    /**
     * Explicitly stop location tracking (usually called when the app is being destroyed).
     */
    fun stopTracking() {
        scope.launch {
            appliedSettings = null
            scheduledLocationTrackingService.stopScheduledTracking()
            optimizedBackgroundLocationRegistrar.stop()
            context.stopActivityAwareLocationService()
        }
    }
}
