package app.logdate.client.health.datasource

import app.logdate.client.health.HealthDataAvailability
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Unavailable implementation of RemoteHealthDataSource that can be used as a fallback
 * if platform-specific implementations fail or are unavailable.
 *
 * This implementation returns empty or false results for all methods.
 */
class UnavailableRemoteHealthDataSource : RemoteHealthDataSource {
    override suspend fun getAvailability(): HealthDataAvailability {
        Napier.d("UnavailableRemoteHealthDataSource.getAvailability() called")
        return HealthDataAvailability.NOT_AVAILABLE
    }

    override suspend fun isAvailable(): Boolean {
        Napier.d("UnavailableRemoteHealthDataSource.isAvailable() called")
        return false
    }

    override suspend fun hasSleepPermissions(): Boolean {
        Napier.d("UnavailableRemoteHealthDataSource.hasSleepPermissions() called")
        return false
    }

    override suspend fun requestSleepPermissions(): Boolean {
        Napier.d("UnavailableRemoteHealthDataSource.requestSleepPermissions() called")
        return false
    }

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> {
        Napier.d("UnavailableRemoteHealthDataSource.getSleepSessions() called")
        return emptyList()
    }

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        Napier.d("UnavailableRemoteHealthDataSource.getAverageWakeUpTime() called")
        return null
    }

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        Napier.d("UnavailableRemoteHealthDataSource.getAverageSleepTime() called")
        return null
    }
}
