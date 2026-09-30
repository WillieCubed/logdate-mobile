package app.logdate.client.location.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Subscription state, not a guarantee of delivery or a complete location history. */
enum class LocationCaptureStatus { Stopped, Starting, Running, Failed }

/** Called on the service's main thread; tokens reject completions from superseded requests. */
internal class LocationCaptureStatusTracker {
    private val mutableStatus = MutableStateFlow(LocationCaptureStatus.Stopped)
    val status: StateFlow<LocationCaptureStatus> = mutableStatus.asStateFlow()
    private var generation = 0L

    fun beginRequest(): Long {
        generation++
        mutableStatus.value = LocationCaptureStatus.Starting
        return generation
    }

    fun completeRequest(
        token: Long,
        succeeded: Boolean,
    ): Boolean {
        if (token != generation || mutableStatus.value != LocationCaptureStatus.Starting) return false
        mutableStatus.value = if (succeeded) LocationCaptureStatus.Running else LocationCaptureStatus.Failed
        return true
    }

    fun stop() {
        generation++
        mutableStatus.value = LocationCaptureStatus.Stopped
    }
}
