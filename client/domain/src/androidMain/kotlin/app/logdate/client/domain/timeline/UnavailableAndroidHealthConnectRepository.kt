package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Android Health Connect repository used when the runtime integration is unavailable.
 *
 * It preserves default day-boundary behavior and explicitly reports Health Connect
 * sleep data as unavailable.
 */
class UnavailableAndroidHealthConnectRepository : HealthConnectRepository {
    private val defaultRepository = DefaultHealthConnectRepository()

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
    ): DayBounds {
        Napier.d("Health Connect integration unavailable; using default day-boundary repository")
        return defaultRepository.getDayBoundsForDate(date, timeZone)
    }

    override suspend fun isHealthConnectAvailable(): Boolean = false

    override suspend fun hasSleepPermissions(): Boolean = false

    override suspend fun requestSleepPermissions(): Boolean = false
}
