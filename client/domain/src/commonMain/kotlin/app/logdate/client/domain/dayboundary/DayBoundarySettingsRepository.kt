package app.logdate.client.domain.dayboundary

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing how the app determines where one day ends and
 * the next begins.
 *
 * The primary control is whether the user wants Health Connect sleep sessions to define
 * day boundaries. Access readiness is handled separately; if Health Connect is unavailable
 * or permissions are missing, the preference is preserved and the runtime falls back to
 * the user's preferred start/end hours or a 4 AM default.
 */
interface DayBoundarySettingsRepository {
    suspend fun getSettings(): DayBoundarySettings

    fun observeSettings(): Flow<DayBoundarySettings>

    suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean)
}
