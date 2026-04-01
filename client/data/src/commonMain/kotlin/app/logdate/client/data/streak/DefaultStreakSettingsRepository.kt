package app.logdate.client.data.streak

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Persists streak preferences (enabled/disabled) and the most recently computed
 * streak count to [KeyValueStorage], allowing the UI to display cached data
 * without recalculating on every screen visit.
 */
class DefaultStreakSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : StreakSettingsRepository {
    companion object {
        private const val KEY_STREAK_ENABLED = "streak_enabled"
        private const val KEY_STREAK_CACHED_VALUE = "streak_cached_value"
    }

    override fun observeStreakEnabled(): Flow<Boolean> = keyValueStorage.observeBoolean(KEY_STREAK_ENABLED, true)

    override suspend fun isStreakEnabled(): Boolean = keyValueStorage.getBoolean(KEY_STREAK_ENABLED, true)

    override suspend fun setStreakEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_STREAK_ENABLED, enabled)
    }

    override fun observeCachedStreak(): Flow<Int> = keyValueStorage.observeInt(KEY_STREAK_CACHED_VALUE, 0)

    override suspend fun getCachedStreak(): Int = keyValueStorage.getInt(KEY_STREAK_CACHED_VALUE, 0)

    override suspend fun setCachedStreak(value: Int) {
        keyValueStorage.putInt(KEY_STREAK_CACHED_VALUE, value)
    }
}
