package app.logdate.client.domain.dayboundary

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing how the app determines where one day ends and
 * the next begins.
 *
 * The primary control is whether to use Health Connect sleep sessions to define
 * day boundaries. When disabled, the app uses the user's preferred start/end
 * hours or a 4 AM default. This setting is user-facing and accessible from
 * Settings > Timeline > Day boundaries.
 */
interface DayBoundarySettingsRepository {
    suspend fun getSettings(): DayBoundarySettings

    fun observeSettings(): Flow<DayBoundarySettings>

    suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean)
}
