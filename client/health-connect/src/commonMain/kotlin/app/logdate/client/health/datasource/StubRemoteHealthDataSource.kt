package app.logdate.client.health.datasource

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Stub implementation of RemoteHealthDataSource that can be used as a fallback
 * if platform-specific implementations fail or are unavailable.
 * 
 * This implementation returns empty or false results for all methods.
 */
class StubRemoteHealthDataSource : RemoteHealthDataSource {
    
    override suspend fun isAvailable(): Boolean {
        Napier.d("StubRemoteHealthDataSource.isAvailable() called")
        return false
    }
    
    override suspend fun hasSleepPermissions(): Boolean {
        Napier.d("StubRemoteHealthDataSource.hasSleepPermissions() called")
        return false
    }
    
    override suspend fun requestSleepPermissions(): Boolean {
        Napier.d("StubRemoteHealthDataSource.requestSleepPermissions() called")
        return false
    }
    
    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        Napier.d("StubRemoteHealthDataSource.getSleepSessions() called")
        return emptyList()
    }
    
    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        Napier.d("StubRemoteHealthDataSource.getAverageWakeUpTime() called")
        return null
    }
    
    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        Napier.d("StubRemoteHealthDataSource.getAverageSleepTime() called")
        return null
    }
}