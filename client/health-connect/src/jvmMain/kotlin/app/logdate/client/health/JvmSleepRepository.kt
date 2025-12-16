package app.logdate.client.health

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * JVM implementation of SleepRepository.
 * This is a stub implementation for desktop platforms.
 */
class JvmSleepRepository : SleepRepository {
    
    override suspend fun hasSleepPermissions(): Boolean {
        // Sleep data is not available on desktop
        return false
    }
    
    override suspend fun requestSleepPermissions(): Boolean {
        // Sleep permissions are not applicable on desktop
        return false
    }
    
    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        // No sleep data available on desktop
        return emptyList()
    }
    
    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        // No sleep data available on desktop
        return null
    }
    
    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        // No sleep data available on desktop
        return null
    }
}