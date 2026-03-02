package app.logdate.client.sensor.battery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A stub implementation of [BatteryInfoProvider] for testing or platforms
 * where battery information is not available.
 */
class StubBatteryInfoProvider : BatteryInfoProvider {
    private val batteryStateFlow =
        MutableStateFlow(
            BatteryState(
                level = 100,
                isCharging = true,
                isPowerSaveMode = false,
            ),
        )

    override val currentBatteryState: Flow<BatteryState> = batteryStateFlow

    override suspend fun getCurrentBatteryState(): BatteryState = batteryStateFlow.value

    override suspend fun isPowerSaveMode(): Boolean = batteryStateFlow.value.isPowerSaveMode

    /**
     * Set a custom battery state for testing purposes.
     */
    fun setBatteryState(batteryState: BatteryState) {
        batteryStateFlow.value = batteryState
    }

    override fun cleanup() {
        // No-op for stub implementation
    }
}
