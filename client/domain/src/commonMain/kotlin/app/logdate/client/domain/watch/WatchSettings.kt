package app.logdate.client.domain.watch

import kotlinx.coroutines.flow.Flow

/**
 * Settings for what data syncs between the phone and a paired Wear OS watch.
 */
data class WatchSyncSettings(
    val syncVoiceNotes: Boolean = true,
    val syncTextEntries: Boolean = true,
    val syncMoodCheckIns: Boolean = true,
    val syncHealthData: Boolean = true,
    val autoSync: Boolean = true,
)

/**
 * Settings for notifications triggered by watch sync events.
 */
data class WatchNotificationSettings(
    val showEntryNotifications: Boolean = true,
    val includeAudioPreview: Boolean = false,
)

/**
 * Repository for watch-related user preferences.
 */
interface WatchSettingsRepository {
    fun observeSyncSettings(): Flow<WatchSyncSettings>

    fun observeNotificationSettings(): Flow<WatchNotificationSettings>

    suspend fun setSyncVoiceNotes(enabled: Boolean)

    suspend fun setSyncTextEntries(enabled: Boolean)

    suspend fun setSyncMoodCheckIns(enabled: Boolean)

    suspend fun setSyncHealthData(enabled: Boolean)

    suspend fun setAutoSync(enabled: Boolean)

    suspend fun setShowEntryNotifications(enabled: Boolean)

    suspend fun setIncludeAudioPreview(enabled: Boolean)
}
