package app.logdate.client.location.tracking

import app.logdate.client.location.settings.LocationTrackingSettingsRepository

class LocationRecordingControls(
    private val settings: LocationTrackingSettingsRepository,
) {
    suspend fun pause() = settings.setBackgroundTrackingEnabled(false)

    suspend fun resume() = settings.setBackgroundTrackingEnabled(true)
}
