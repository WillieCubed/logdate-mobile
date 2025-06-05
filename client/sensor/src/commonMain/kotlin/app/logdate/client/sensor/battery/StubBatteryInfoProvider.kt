package app.logdate.client.sensor.battery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A stub implementation of [BatteryInfoProvider] for testing or platforms
 * where battery information is not available.
 */
class StubBatteryInfoProvider : BatteryInfoProvider {
    private val _batteryState = MutableStateFlow(
        BatteryState(
            level = 100,
            isCharging = true,
            isPowerSaveMode = false
        )
    )
    
    override val currentBatteryState: Flow<BatteryState> = _batteryState
    
    override suspend fun getCurrentBatteryState(): BatteryState {
        return _batteryState.value
    }
    
    override suspend fun isPowerSaveMode(): Boolean {
        return _batteryState.value.isPowerSaveMode
    }
    
    /**
     * Set a custom battery state for testing purposes.
     */
    fun setBatteryState(batteryState: BatteryState) {
        _batteryState.value = batteryState
    }
    
    override fun cleanup() {
        // No-op for stub implementation
    }
}