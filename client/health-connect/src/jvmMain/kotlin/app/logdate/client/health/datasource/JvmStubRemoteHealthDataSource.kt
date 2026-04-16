package app.logdate.client.health.datasource

import app.logdate.client.health.HealthDataAvailability
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * JVM implementation of [RemoteHealthDataSource] that provides stub data.
 *
 * This implementation is used on desktop platforms where no health API is available.
 * It always returns empty or null results for health data queries, but can be extended
 * to provide simulated data for testing or development.
 */
class JvmStubRemoteHealthDataSource : RemoteHealthDataSource {
    override suspend fun getAvailability(): HealthDataAvailability {
        Napier.d("JvmStubRemoteHealthDataSource health provider is not available")
        return HealthDataAvailability.NOT_AVAILABLE
    }

    override suspend fun isAvailable(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource is not available")
        return false
    }

    override suspend fun hasSleepPermissions(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource has no health permissions")
        return false
    }

    override suspend fun requestSleepPermissions(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource cannot request health permissions")
        return false
    }

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> {
        Napier.d("JvmStubRemoteHealthDataSource returns empty sleep sessions list")
        return emptyList()
    }

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        Napier.d("JvmStubRemoteHealthDataSource returns null for average wake-up time")
        return null
    }

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        Napier.d("JvmStubRemoteHealthDataSource returns null for average sleep time")
        return null
    }
}
