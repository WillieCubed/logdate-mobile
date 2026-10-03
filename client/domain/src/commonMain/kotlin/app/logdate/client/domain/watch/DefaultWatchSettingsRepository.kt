package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [WatchSettingsRepository] using [KeyValueStorage].
 */
class DefaultWatchSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchSettingsRepository {
    companion object {
        private const val KEY_SHOW_ENTRY_NOTIFICATIONS = "watch_show_entry_notifications"
    }

    override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
        keyValueStorage
            .observeBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, true)
            .map { showEntryNotifications -> WatchNotificationSettings(showEntryNotifications = showEntryNotifications) }

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_SHOW_ENTRY_NOTIFICATIONS, enabled)
    }
}
