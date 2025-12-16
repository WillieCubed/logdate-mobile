package app.logdate.client.domain.timeline

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Desktop implementation of HealthConnectRepository.
 * 
 * Desktop platforms don't have a health data API, so this uses the default implementation.
 */
class DesktopHealthConnectRepository : HealthConnectRepository {
    private val defaultRepository = DefaultHealthConnectRepository()
    
    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
        return defaultRepository.getDayBoundsForDate(date, timeZone)
    }
    
    override suspend fun isHealthConnectAvailable(): Boolean = false
    
    override suspend fun hasSleepPermissions(): Boolean = false
    
    override suspend fun requestSleepPermissions(): Boolean = false
}