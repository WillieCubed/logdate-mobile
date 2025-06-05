package app.logdate.client.sensor.battery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of BatteryInfoProvider for JVM platforms.
 * 
 * This implementation provides reasonable defaults for JVM platforms.
 * For desktop-specific implementations, see [DesktopBatteryInfoProvider].
 */
class JvmBatteryInfoProvider : BatteryInfoProvider {
    
    // Default to a reasonable battery state for JVM
    private val _batteryState = MutableStateFlow(
        BatteryState(
            level = 100,
            isCharging = true,
            isPowerSaveMode = false
        )
    )
    
    override val currentBatteryState: Flow<BatteryState> = _batteryState.asStateFlow()
    
    override suspend fun getCurrentBatteryState(): BatteryState {
        return _batteryState.value
    }
    
    override suspend fun isPowerSaveMode(): Boolean {
        return false
    }
    
    override fun cleanup() {
        // No-op for JVM
    }
}