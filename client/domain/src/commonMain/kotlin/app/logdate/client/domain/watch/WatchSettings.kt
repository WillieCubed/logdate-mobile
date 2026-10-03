package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow

/**
 * Settings for notifications triggered by watch sync events.
 */
data class WatchNotificationSettings(
    val showEntryNotifications: Boolean = true,
)

/**
 * Repository for watch-related user preferences.
 */
interface WatchSettingsRepository {
    fun observeNotificationSettings(): Flow<WatchNotificationSettings>

    suspend fun setShowEntryNotifications(enabled: Boolean)
}
