package app.logdate.client.health.data

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Desktop implementation of RemoteHealthDataSource.
 * This is a stub implementation since desktop platforms don't have health APIs.
 */
class DesktopLocalHealthDataSource : RemoteHealthDataSource {
    
    override suspend fun isHealthApiAvailable(): Boolean {
        // Health APIs are not available on desktop
        return false
    }

    override suspend fun hasSleepPermissions(): Boolean {
        // Sleep permissions are not applicable on desktop
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

    override suspend fun getAvailableDataTypes(): List<String> {
        // No health data types available on desktop
        return emptyList()
    }
}