package app.logdate.client.location.settings

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [LocationTrackingSettingsRepository] using [KeyValueStorage].
 */
class DefaultLocationTrackingSettingsRepository(
    private val keyValueStorage: KeyValueStorage
) : LocationTrackingSettingsRepository {

    companion object {
        private const val KEY_BACKGROUND_TRACKING_ENABLED = "location_background_tracking_enabled"
        private const val KEY_TRACKING_INTERVAL_MINUTES = "location_tracking_interval_minutes"
        private const val KEY_AUTO_TRACK_JOURNAL = "location_auto_track_journal"
        private const val KEY_AUTO_TRACK_TIMELINE = "location_auto_track_timeline"
        
        // Default values
        private const val DEFAULT_TRACKING_INTERVAL_MINUTES = 30L
    }
    
    override suspend fun getSettings(): LocationTrackingSettings {
        return LocationTrackingSettings(
            backgroundTrackingEnabled = keyValueStorage.getBoolean(
                KEY_BACKGROUND_TRACKING_ENABLED, 
                false
            ),
            trackingIntervalMinutes = keyValueStorage.getLong(
                KEY_TRACKING_INTERVAL_MINUTES, 
                DEFAULT_TRACKING_INTERVAL_MINUTES
            ).coerceAtLeast(15),
            autoTrackForJournalEntries = keyValueStorage.getBoolean(
                KEY_AUTO_TRACK_JOURNAL, 
                true
            ),
            autoTrackForTimelineReview = keyValueStorage.getBoolean(
                KEY_AUTO_TRACK_TIMELINE, 
                true
            )
        )
    }
    
    override fun observeSettings(): Flow<LocationTrackingSettings> {
        return combine(
            keyValueStorage.observeBoolean(KEY_BACKGROUND_TRACKING_ENABLED, false),
            keyValueStorage.observeLong(KEY_TRACKING_INTERVAL_MINUTES, DEFAULT_TRACKING_INTERVAL_MINUTES)
                .map { it.coerceAtLeast(15) },
            keyValueStorage.observeBoolean(KEY_AUTO_TRACK_JOURNAL, true),
            keyValueStorage.observeBoolean(KEY_AUTO_TRACK_TIMELINE, true)
        ) { backgroundEnabled, interval, autoJournal, autoTimeline ->
            LocationTrackingSettings(
                backgroundTrackingEnabled = backgroundEnabled,
                trackingIntervalMinutes = interval,
                autoTrackForJournalEntries = autoJournal,
                autoTrackForTimelineReview = autoTimeline
            )
        }
    }
    
    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        Napier.i("Updating location tracking settings: $settings")
        
        keyValueStorage.putBoolean(
            KEY_BACKGROUND_TRACKING_ENABLED, 
            settings.backgroundTrackingEnabled
        )
        
        keyValueStorage.putLong(
            KEY_TRACKING_INTERVAL_MINUTES,
            settings.trackingIntervalMinutes.coerceAtLeast(15)
        )
        
        keyValueStorage.putBoolean(
            KEY_AUTO_TRACK_JOURNAL,
            settings.autoTrackForJournalEntries
        )
        
        keyValueStorage.putBoolean(
            KEY_AUTO_TRACK_TIMELINE,
            settings.autoTrackForTimelineReview
        )
    }
    
    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        Napier.i("Setting background tracking enabled: $enabled")
        keyValueStorage.putBoolean(KEY_BACKGROUND_TRACKING_ENABLED, enabled)
    }
    
    override suspend fun setTrackingInterval(intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(15)
        Napier.i("Setting tracking interval: $safeInterval minutes")
        keyValueStorage.putLong(KEY_TRACKING_INTERVAL_MINUTES, safeInterval)
    }
}