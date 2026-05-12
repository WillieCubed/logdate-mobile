package app.logdate.client.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * [GyroSensorProvider] for platforms without gyroscope access.
 */
class UnavailableGyroSensorProvider(
    coroutineScope: CoroutineScope,
) : GyroSensorProvider {
    private val _currentValue = MutableSharedFlow<GyroOffset>()

    override val currentValue: SharedFlow<GyroOffset> =
        _currentValue.shareIn(
            coroutineScope,
            started = SharingStarted.WhileSubscribed(),
        )

    override fun cleanup() {
    }
}
