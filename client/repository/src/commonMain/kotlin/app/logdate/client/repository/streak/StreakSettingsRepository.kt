package app.logdate.client.repository.streak

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing streak tracking preferences and cached streak value.
 */
interface StreakSettingsRepository {
    fun observeStreakEnabled(): Flow<Boolean>

    suspend fun isStreakEnabled(): Boolean

    suspend fun setStreakEnabled(enabled: Boolean)

    fun observeCachedStreak(): Flow<Int>

    suspend fun getCachedStreak(): Int

    suspend fun setCachedStreak(value: Int)
}
