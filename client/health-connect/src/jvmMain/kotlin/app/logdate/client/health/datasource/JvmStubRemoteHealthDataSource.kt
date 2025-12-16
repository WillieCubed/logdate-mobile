package app.logdate.client.health.datasource

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * JVM implementation of [RemoteHealthDataSource] that provides stub data.
 * 
 * This implementation is used on desktop platforms where no health API is available.
 * It always returns empty or null results for health data queries, but can be extended
 * to provide simulated data for testing or development.
 */
class JvmStubRemoteHealthDataSource : RemoteHealthDataSource {
    
    override suspend fun isAvailable(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource is always available")
        return true
    }
    
    override suspend fun hasSleepPermissions(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource always has permissions")
        return true
    }
    
    override suspend fun requestSleepPermissions(): Boolean {
        Napier.d("JvmStubRemoteHealthDataSource permissions request always succeeds")
        return true
    }
    
    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        Napier.d("JvmStubRemoteHealthDataSource returns empty sleep sessions list")
        return emptyList()
    }
    
    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        Napier.d("JvmStubRemoteHealthDataSource returns null for average wake-up time")
        return null
    }
    
    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        Napier.d("JvmStubRemoteHealthDataSource returns null for average sleep time")
        return null
    }
}