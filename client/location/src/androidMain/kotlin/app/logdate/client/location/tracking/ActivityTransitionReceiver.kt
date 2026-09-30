package app.logdate.client.location.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.location.ActivityHistoryItem
import app.logdate.client.repository.location.ActivityHistoryRepository
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.time.Clock
import kotlin.time.Duration.Companion.nanoseconds

/** Saves transition evidence even when there is no running service instance. */
class ActivityTransitionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val ownerProvider: CanonicalOwnerProvider by inject()
    private val repository: ActivityHistoryRepository by inject()
    private val settingsRepository: LocationTrackingSettingsRepository by inject()
    private val deviceIdProvider: DeviceIdProvider by inject()
    private val clock: Clock by inject()
    private val dispatcher: CoroutineDispatcher by inject(named("io-dispatcher"))

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val recordedAt = clock.now()
        val elapsedNow = SystemClock.elapsedRealtimeNanos()
        val zoneId = TimeZone.currentSystemDefault().id
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            try {
                val settings = settingsRepository.getSettings()
                if (!settings.backgroundTrackingEnabled || settings.captureMode != LocationCaptureMode.ACTIVE) return@launch
                val ownerId = ownerProvider.getCanonicalOwnerId()
                val deviceId = deviceIdProvider.getDeviceId().value.toString()
                for (event in result.transitionEvents) {
                    val timestamp = recordedAt - (elapsedNow - event.elapsedRealTimeNanos).coerceAtLeast(0).nanoseconds
                    repository
                        .recordActivity(
                            ActivityHistoryItem(
                                id = activityEvidenceId(deviceId, event.elapsedRealTimeNanos, event.activityType, event.transitionType),
                                userId = ownerId,
                                deviceId = deviceId,
                                timestamp = timestamp,
                                recordedAt = recordedAt,
                                activityType = event.activityType.activityLabel(),
                                transitionType =
                                    if (event.transitionType ==
                                        ActivityTransition.ACTIVITY_TRANSITION_ENTER
                                    ) {
                                        "ENTER"
                                    } else {
                                        "EXIT"
                                    },
                                timeZoneId = zoneId,
                            ),
                        ).onFailure { Napier.w("Failed to persist activity transition", it) }
                    Handler(Looper.getMainLooper()).post {
                        ActivityAwareLocationService.instance?.onActivityTransition(event.activityType, event.transitionType, timestamp)
                    }
                }
            } catch (error: Exception) {
                Napier.w("Failed to receive activity transitions", error)
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun Int.activityLabel(): String =
    when (this) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        else -> "UNKNOWN"
    }
