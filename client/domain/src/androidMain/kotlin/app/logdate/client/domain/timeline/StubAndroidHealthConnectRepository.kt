package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Stub implementation of the Android Health Connect repository.
 * 
 * This is a temporary implementation that uses the default behavior
 * while we resolve the actual Health Connect API integration.
 */
class StubAndroidHealthConnectRepository : HealthConnectRepository {
    private val defaultRepository = DefaultHealthConnectRepository()
    
    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
        Napier.d("Using stub Android Health Connect repository")
        return defaultRepository.getDayBoundsForDate(date, timeZone)
    }
    
    override suspend fun isHealthConnectAvailable(): Boolean = false
    
    override suspend fun hasSleepPermissions(): Boolean = false
    
    override suspend fun requestSleepPermissions(): Boolean = false
}