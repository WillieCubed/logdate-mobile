package app.logdate.client.sensor.battery

import kotlinx.coroutines.flow.Flow

/**
 * Provides information about the device's battery status.
 * 
 * This interface allows access to battery information such as power save mode status,
 * battery level, and charging state. Implementations should handle platform-specific
 * battery monitoring and provide a consistent API across platforms.
 */
interface BatteryInfoProvider {
    /**
     * Current battery information as a [BatteryState]
     */
    val currentBatteryState: Flow<BatteryState>
    
    /**
     * Returns the current battery state without subscribing to updates.
     */
    suspend fun getCurrentBatteryState(): BatteryState
    
    /**
     * Returns whether the device is currently in power save mode.
     * This is a convenience method for when only power save mode status is needed.
     */
    suspend fun isPowerSaveMode(): Boolean
    
    /**
     * Releases resources and observers associated with battery monitoring.
     */
    fun cleanup()
}