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
    private val keyValueStorage: KeyValueStorage,
) : LocationTrackingSettingsRepository {
    companion object {
        private const val KEY_BACKGROUND_TRACKING_ENABLED = "location_background_tracking_enabled"
        private const val KEY_MINIMUM_PERSIST_INTERVAL_MINUTES = "location_tracking_interval_minutes"
        private const val KEY_AUTO_TRACK_JOURNAL = "location_auto_track_journal"
        private const val KEY_AUTO_TRACK_TIMELINE = "location_auto_track_timeline"
        private const val KEY_CAPTURE_MODE = "location_capture_mode"
        private const val KEY_SERVER_ASSIST_ENABLED = "location_server_assist_enabled"

        // Default values
        private const val DEFAULT_TRACKING_INTERVAL_MINUTES = 30L
    }

    override suspend fun getSettings(): LocationTrackingSettings =
        LocationTrackingSettings(
            backgroundTrackingEnabled =
                keyValueStorage.getBoolean(
                    KEY_BACKGROUND_TRACKING_ENABLED,
                    false,
                ),
            minimumPersistIntervalMinutes =
                keyValueStorage
                    .getLong(
                        KEY_MINIMUM_PERSIST_INTERVAL_MINUTES,
                        DEFAULT_TRACKING_INTERVAL_MINUTES,
                    ).coerceAtLeast(2),
            captureMode = parseCaptureMode(keyValueStorage.getString(KEY_CAPTURE_MODE)),
            serverAssistEnabled =
                keyValueStorage.getBoolean(
                    KEY_SERVER_ASSIST_ENABLED,
                    false,
                ),
            autoTrackForJournalEntries =
                keyValueStorage.getBoolean(
                    KEY_AUTO_TRACK_JOURNAL,
                    true,
                ),
            autoTrackForTimelineReview =
                keyValueStorage.getBoolean(
                    KEY_AUTO_TRACK_TIMELINE,
                    true,
                ),
        )

    override fun observeSettings(): Flow<LocationTrackingSettings> =
        combine(
            combine(
                keyValueStorage.observeBoolean(KEY_BACKGROUND_TRACKING_ENABLED, false),
                keyValueStorage
                    .observeLong(KEY_MINIMUM_PERSIST_INTERVAL_MINUTES, DEFAULT_TRACKING_INTERVAL_MINUTES)
                    .map { it.coerceAtLeast(2) },
                keyValueStorage
                    .observeString(KEY_CAPTURE_MODE)
                    .map(::parseCaptureMode),
                keyValueStorage.observeBoolean(KEY_SERVER_ASSIST_ENABLED, false),
            ) { backgroundEnabled, interval, captureMode, serverAssistEnabled ->
                BaseSettingsSnapshot(
                    backgroundEnabled = backgroundEnabled,
                    interval = interval,
                    captureMode = captureMode,
                    serverAssistEnabled = serverAssistEnabled,
                )
            },
            keyValueStorage.observeBoolean(KEY_AUTO_TRACK_JOURNAL, true),
            keyValueStorage.observeBoolean(KEY_AUTO_TRACK_TIMELINE, true),
        ) { base, autoJournal, autoTimeline ->
            LocationTrackingSettings(
                backgroundTrackingEnabled = base.backgroundEnabled,
                minimumPersistIntervalMinutes = base.interval,
                captureMode = base.captureMode,
                serverAssistEnabled = base.serverAssistEnabled,
                autoTrackForJournalEntries = autoJournal,
                autoTrackForTimelineReview = autoTimeline,
            )
        }

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        Napier.i("Updating location tracking settings: $settings")

        keyValueStorage.putBoolean(
            KEY_BACKGROUND_TRACKING_ENABLED,
            settings.backgroundTrackingEnabled,
        )

        keyValueStorage.putLong(
            KEY_MINIMUM_PERSIST_INTERVAL_MINUTES,
            settings.minimumPersistIntervalMinutes.coerceAtLeast(2),
        )

        keyValueStorage.putString(
            KEY_CAPTURE_MODE,
            settings.captureMode.name,
        )

        keyValueStorage.putBoolean(
            KEY_SERVER_ASSIST_ENABLED,
            settings.serverAssistEnabled,
        )

        keyValueStorage.putBoolean(
            KEY_AUTO_TRACK_JOURNAL,
            settings.autoTrackForJournalEntries,
        )

        keyValueStorage.putBoolean(
            KEY_AUTO_TRACK_TIMELINE,
            settings.autoTrackForTimelineReview,
        )
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        Napier.i("Setting background tracking enabled: $enabled")
        keyValueStorage.putBoolean(KEY_BACKGROUND_TRACKING_ENABLED, enabled)
    }

    override suspend fun setTrackingInterval(intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(2)
        Napier.i("Setting tracking interval: $safeInterval minutes")
        keyValueStorage.putLong(KEY_MINIMUM_PERSIST_INTERVAL_MINUTES, safeInterval)
    }

    override suspend fun setCaptureMode(mode: LocationCaptureMode) {
        Napier.i("Setting capture mode: $mode")
        keyValueStorage.putString(KEY_CAPTURE_MODE, mode.name)
    }

    override suspend fun setServerAssistEnabled(enabled: Boolean) {
        Napier.i("Setting server assist enabled: $enabled")
        keyValueStorage.putBoolean(KEY_SERVER_ASSIST_ENABLED, enabled)
    }

    private fun parseCaptureMode(value: String?): LocationCaptureMode =
        runCatching {
            LocationCaptureMode.valueOf(value ?: LocationCaptureMode.STABLE.name)
        }.getOrElse {
            Napier.w("Unknown stored location capture mode '$value'; falling back to STABLE")
            LocationCaptureMode.STABLE
        }

    private data class BaseSettingsSnapshot(
        val backgroundEnabled: Boolean,
        val interval: Long,
        val captureMode: LocationCaptureMode,
        val serverAssistEnabled: Boolean,
    )
}
