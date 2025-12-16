package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * Default implementation of HealthConnectRepository for platforms without Health Connect support.
 * 
 * This implementation provides reasonable defaults for day bounds.
 */
class DefaultHealthConnectRepository : HealthConnectRepository {
    
    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
        Napier.d("Using default day bounds for date: $date")
        
        // Default day starts at 5:00 AM to accommodate early risers
        val startOfDay = date.atStartOfDayIn(timeZone)
        val dayStart = Instant.fromEpochSeconds(startOfDay.epochSeconds + 5 * 60 * 60)
        
        // Default day ends at midnight (next day)
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val dayEnd = nextDay.atStartOfDayIn(timeZone)
        
        return DayBounds(start = dayStart, end = dayEnd)
    }
    
    override suspend fun isHealthConnectAvailable(): Boolean = false
    
    override suspend fun hasSleepPermissions(): Boolean = false
    
    override suspend fun requestSleepPermissions(): Boolean = false
    
    companion object {
        private const val HOUR_IN_SECONDS = 60 * 60L
    }
}