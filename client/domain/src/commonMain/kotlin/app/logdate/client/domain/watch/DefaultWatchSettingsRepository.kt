package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Default implementation of [WatchSettingsRepository] using [KeyValueStorage].
 */
class DefaultWatchSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchSettingsRepository {
    companion object {
        private const val KEY_SYNC_VOICE_NOTES = "watch_sync_voice_notes"
        private const val KEY_SYNC_TEXT_ENTRIES = "watch_sync_text_entries"
        private const val KEY_SYNC_MOOD_CHECKINS = "watch_sync_mood_checkins"
        private const val KEY_SYNC_HEALTH_DATA = "watch_sync_health_data"
        private const val KEY_AUTO_SYNC = "watch_auto_sync"
        private const val KEY_SHOW_ENTRY_NOTIFICATIONS = "watch_show_entry_notifications"
        private const val KEY_INCLUDE_AUDIO_PREVIEW = "watch_include_audio_preview"
    }

    override fun observeSyncSettings(): Flow<WatchSyncSettings> =
        combine(
            keyValueStorage.observeBoolean(KEY_SYNC_VOICE_NOTES, true),
            keyValueStorage.observeBoolean(KEY_SYNC_TEXT_ENTRIES, true),
            keyValueStorage.observeBoolean(KEY_SYNC_MOOD_CHECKINS, true),
            keyValueStorage.observeBoolean(KEY_SYNC_HEALTH_DATA, true),
            keyValueStorage.observeBoolean(KEY_AUTO_SYNC, true),
        ) { voiceNotes, text, mood, health, auto ->
            WatchSyncSettings(
                syncVoiceNotes = voiceNotes,
                syncTextEntries = text,
                syncMoodCheckIns = mood,
                syncHealthData = health,
                autoSync = auto,
            )
        }

    override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
        combine(
            keyValueStorage.observeBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, true),
            keyValueStorage.observeBoolean(KEY_INCLUDE_AUDIO_PREVIEW, false),
        ) { showNotifications, audioPreview ->
            WatchNotificationSettings(
                showEntryNotifications = showNotifications,
                includeAudioPreview = audioPreview,
            )
        }

    override suspend fun setSyncVoiceNotes(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SYNC_VOICE_NOTES, enabled)
    }

    override suspend fun setSyncTextEntries(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SYNC_TEXT_ENTRIES, enabled)
    }

    override suspend fun setSyncMoodCheckIns(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SYNC_MOOD_CHECKINS, enabled)
    }

    override suspend fun setSyncHealthData(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SYNC_HEALTH_DATA, enabled)
    }

    override suspend fun setAutoSync(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_AUTO_SYNC, enabled)
    }

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, enabled)
    }

    override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_INCLUDE_AUDIO_PREVIEW, enabled)
    }
}
