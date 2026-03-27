package app.logdate.client.domain.dayboundary

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.health.LocalFirstHealthRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [DayBoundarySettingsRepository] using [KeyValueStorage].
 */
class DefaultDayBoundarySettingsRepository(
    private val keyValueStorage: KeyValueStorage,
    private val healthRepository: LocalFirstHealthRepository,
) : DayBoundarySettingsRepository {
    companion object {
        private const val KEY_SLEEP_BASED_ENABLED = "day_boundary_sleep_based_enabled"
    }

    override suspend fun getSettings(): DayBoundarySettings {
        val storedEnabled = keyValueStorage.getBoolean(KEY_SLEEP_BASED_ENABLED, false)
        val effectiveEnabled = sanitizeSleepBasedBoundariesEnabled(storedEnabled, persistCorrection = true)
        return DayBoundarySettings(sleepBasedBoundariesEnabled = effectiveEnabled)
    }

    override fun observeSettings(): Flow<DayBoundarySettings> =
        flow {
            emit(getSettings())
            emitAll(
                keyValueStorage
                    .observeBoolean(KEY_SLEEP_BASED_ENABLED, false)
                    .drop(1)
                    .map { enabled ->
                        DayBoundarySettings(
                            sleepBasedBoundariesEnabled =
                                sanitizeSleepBasedBoundariesEnabled(enabled, persistCorrection = true),
                        )
                    },
            )
        }

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {
        val effectiveEnabled = sanitizeSleepBasedBoundariesEnabled(enabled, persistCorrection = false)
        Napier.i("Setting sleep-based boundaries enabled: requested=$enabled effective=$effectiveEnabled")
        keyValueStorage.putBoolean(KEY_SLEEP_BASED_ENABLED, effectiveEnabled)
    }

    private suspend fun sanitizeSleepBasedBoundariesEnabled(
        requestedEnabled: Boolean,
        persistCorrection: Boolean,
    ): Boolean {
        if (!requestedEnabled) {
            return false
        }

        val healthAvailable = healthRepository.isHealthDataAvailable()
        val hasPermissions = healthAvailable && healthRepository.hasSleepPermissions()
        if (healthAvailable && hasPermissions) {
            return true
        }

        Napier.w(
            message =
                "Sleep-based day boundaries cannot remain enabled " +
                    "(healthAvailable=$healthAvailable, hasPermissions=$hasPermissions)",
        )
        if (persistCorrection) {
            keyValueStorage.putBoolean(KEY_SLEEP_BASED_ENABLED, false)
        }
        return false
    }
}
