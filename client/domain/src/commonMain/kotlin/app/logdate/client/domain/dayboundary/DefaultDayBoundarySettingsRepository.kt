package app.logdate.client.domain.dayboundary

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [DayBoundarySettingsRepository] using [KeyValueStorage].
 */
class DefaultDayBoundarySettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : DayBoundarySettingsRepository {
    companion object {
        private const val KEY_SLEEP_BASED_ENABLED = "day_boundary_sleep_based_enabled"
    }

    override suspend fun getSettings(): DayBoundarySettings =
        DayBoundarySettings(
            sleepBasedBoundariesEnabled =
                keyValueStorage.getBoolean(KEY_SLEEP_BASED_ENABLED, true),
        )

    override fun observeSettings(): Flow<DayBoundarySettings> =
        keyValueStorage
            .observeBoolean(KEY_SLEEP_BASED_ENABLED, true)
            .map { enabled ->
                DayBoundarySettings(sleepBasedBoundariesEnabled = enabled)
            }

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {
        Napier.i("Setting sleep-based boundaries enabled: $enabled")
        keyValueStorage.putBoolean(KEY_SLEEP_BASED_ENABLED, enabled)
    }
}
