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
        private const val KEY_SHOW_ENTRY_NOTIFICATIONS = "watch_show_entry_notifications"
        private const val KEY_INCLUDE_AUDIO_PREVIEW = "watch_include_audio_preview"
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

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, enabled)
    }

    override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_INCLUDE_AUDIO_PREVIEW, enabled)
    }
}
