package app.logdate.client.location.settings

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing location tracking settings.
 */
interface LocationTrackingSettingsRepository {
    /**
     * Get the current location tracking settings.
     */
    suspend fun getSettings(): LocationTrackingSettings
    
    /**
     * Observe the location tracking settings for changes.
     */
    fun observeSettings(): Flow<LocationTrackingSettings>
    
    /**
     * Update the location tracking settings.
     */
    suspend fun updateSettings(settings: LocationTrackingSettings)
    
    /**
     * Update the background tracking enabled setting.
     */
    suspend fun setBackgroundTrackingEnabled(enabled: Boolean)
    
    /**
     * Update the tracking interval.
     */
    suspend fun setTrackingInterval(intervalMinutes: Long)
}