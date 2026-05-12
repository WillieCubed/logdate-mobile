package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * iOS implementation of HealthConnectRepository.
 *
 * iOS does not expose Health Connect. Until a HealthKit-backed repository is available, timeline
 * day bounds fall back to the app's deterministic default.
 */
class IosHealthConnectRepository : HealthConnectRepository {
    private val defaultRepository = DefaultHealthConnectRepository()

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
    ): DayBounds {
        Napier.d("HealthKit day bounds unavailable; using default day bounds")
        return defaultRepository.getDayBoundsForDate(date, timeZone)
    }

    override suspend fun isHealthConnectAvailable(): Boolean = false

    override suspend fun hasSleepPermissions(): Boolean = false

    override suspend fun requestSleepPermissions(): Boolean = false
}
