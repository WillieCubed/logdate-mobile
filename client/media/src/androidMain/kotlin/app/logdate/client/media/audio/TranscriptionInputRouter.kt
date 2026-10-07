package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.device.AudioRouteRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TranscriptionInputHealth {
    @Volatile
    var isHealthy = true
}

internal class TranscriptionInputRouter(
    private val onUnconfirmedInput: () -> Unit,
) {
    private val lock = Any()
    private var appliedInput: Pair<TranscriptionService, String?>? = null

    @Volatile
    var health = TranscriptionInputHealth()
        private set

    fun reset() {
        synchronized(lock) {
            health = TranscriptionInputHealth()
            appliedInput = null
        }
    }

    fun update(
        service: TranscriptionService?,
        deviceId: String?,
        verifyActiveInput: Boolean = false,
    ) {
        synchronized(lock) {
            if (service == null || !service.supportsLiveTranscription) return
            val applied = appliedInput
            if (!verifyActiveInput && applied?.first === service && applied.second == deviceId) return
            val routed =
                try {
                    service.updatePreferredInputDevice(deviceId)
                } catch (e: Exception) {
                    Napier.e("Could not switch transcription microphone", e)
                    false
                }
            if (routed) {
                appliedInput = service to deviceId
            } else {
                appliedInput = null
                health.isHealthy = false
                onUnconfirmedInput()
            }
        }
    }
}

internal fun observeRecordingInputRoutes(
    scope: CoroutineScope,
    routes: AudioRouteRepository,
    recorder: RecordingServiceController,
    sessionMutex: Mutex,
    isCurrentSession: () -> Boolean,
    onInputChanged: (String?) -> Unit,
): Job =
    scope.launch {
        routes.requestedInputDeviceIds.collect { deviceId ->
            sessionMutex.withLock {
                if (isCurrentSession()) {
                    onInputChanged(applyRecordingInputRoute(routes, recorder, deviceId))
                }
            }
        }
    }

internal fun applyRecordingInputRoute(
    routes: AudioRouteRepository,
    recorder: RecordingServiceController,
    deviceId: String?,
): String? {
    try {
        recorder.updatePreferredInputDevice(deviceId)
    } catch (e: Exception) {
        Napier.e("Could not switch recording microphone", e)
        routes.updateRecordingInputDevice(
            recorder.serviceState.value?.routedInputDeviceId,
            true,
            "Could not switch microphone. Recording continues on the current input.",
        )
    }
    return recorder.serviceState.value?.routedInputDeviceId ?: deviceId
}
