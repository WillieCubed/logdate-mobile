package app.logdate.client.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * A stub implementation of [GyroSensorProvider] that emits no real sensor data.
 * This can be used on platforms where gyroscope access is not yet implemented.
 */
class StubGyroSensorProvider(
    coroutineScope: CoroutineScope
) : GyroSensorProvider {
    private val _currentValue = MutableSharedFlow<GyroOffset>()

    override val currentValue: SharedFlow<GyroOffset> = _currentValue.shareIn(
        coroutineScope,
        started = SharingStarted.WhileSubscribed(),
    )

    override fun cleanup() {
        // No-op for stub implementation
    }
}