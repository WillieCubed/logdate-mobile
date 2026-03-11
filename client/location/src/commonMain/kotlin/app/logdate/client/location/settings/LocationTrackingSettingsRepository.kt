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
    suspend fun setTrackingInterval(intervalMinutes: Long) {
        updateSettings(getSettings().copy(minimumPersistIntervalMinutes = intervalMinutes))
    }

    /**
     * Update the active capture mode.
     */
    suspend fun setCaptureMode(mode: LocationCaptureMode) {
        updateSettings(getSettings().copy(captureMode = mode))
    }

    /**
     * Update whether optional server assists are enabled.
     */
    suspend fun setServerAssistEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(serverAssistEnabled = enabled))
    }
}
