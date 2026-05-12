package app.logdate.client.health.data

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Desktop [RemoteHealthDataSource] for platforms without health APIs.
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
