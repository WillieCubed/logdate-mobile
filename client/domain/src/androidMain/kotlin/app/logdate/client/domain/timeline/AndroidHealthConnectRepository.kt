package app.logdate.client.domain.timeline

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinInstant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Android implementation of HealthConnectRepository using Health Connect API.
 */
class AndroidHealthConnectRepository(
    private val context: Context
) : HealthConnectRepository {

    private val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Napier.e("Failed to create HealthConnectClient", e)
            null
        }
    }

    private val sleepPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
        val client = healthConnectClient ?: return getDefaultDayBounds(date, timeZone)
        
        if (!hasSleepPermissions()) {
            Napier.d("Sleep permissions not granted, using default day bounds")
            return getDefaultDayBounds(date, timeZone)
        }
        
        try {
            // Look at sleep data from previous 30 days to establish patterns
            val endTime = date.plusDays(1).atStartOfDayIn(timeZone)
            val startTime = date.minusDays(30).atStartOfDayIn(timeZone)
            
            val sleepSessions = readSleepSessions(startTime, endTime)
            
            if (sleepSessions.isEmpty()) {
                Napier.d("No sleep data available, using default day bounds")
                return getDefaultDayBounds(date, timeZone)
            }
            
            // Calculate average wake-up and sleep times
            val wakeUpTimes = mutableListOf<java.time.LocalTime>()
            val sleepTimes = mutableListOf<java.time.LocalTime>()
            
            sleepSessions.forEach { session ->
                val zoneId = ZoneId.of(timeZone.id)
                val startDateTime = session.startTime.atZone(zoneId)
                val endDateTime = session.endTime.atZone(zoneId)
                
                // End time of sleep session is wake-up time
                wakeUpTimes.add(endDateTime.toLocalTime())
                
                // Start time of sleep session is sleep time
                sleepTimes.add(startDateTime.toLocalTime())
            }
            
            // Calculate early, normal and late bounds by analyzing the distribution
            val earlyWakeUpTime = findEarlyPercentile(wakeUpTimes)
            val normalWakeUpTime = calculateAverageTime(wakeUpTimes)
            val avgSleepTime = calculateAverageTime(sleepTimes)
            
            // If no valid averages, use default bounds
            if (normalWakeUpTime == null || avgSleepTime == null) {
                return getDefaultDayBounds(date, timeZone)
            }
            
            // Create day bounds using calculated times
            val zoneId = ZoneId.of(timeZone.id)
            val dateAsJava = date.toJavaLocalDate()
            
            // Use the earlier of earlyWakeUpTime or normalWakeUpTime - 1 hour (with a minimum of 4am)
            val wakeUpTimeToUse = if (earlyWakeUpTime != null) {
                val oneHourEarlier = normalWakeUpTime.minusHours(1)
                if (earlyWakeUpTime.isBefore(oneHourEarlier)) {
                    earlyWakeUpTime
                } else {
                    oneHourEarlier
                }
            } else {
                normalWakeUpTime.minusHours(1)
            }
            
            // Ensure we don't set the start too early (before 4am)
            val fourAM = java.time.LocalTime.of(4, 0)
            val finalWakeUpTime = if (wakeUpTimeToUse.isBefore(fourAM)) fourAM else wakeUpTimeToUse
            
            // Day starts at adjusted wake-up time
            val dayStart = dateAsJava.atTime(finalWakeUpTime)
                .atZone(zoneId)
                .toInstant()
                .toKotlinInstant()
            
            // Day ends at sleep time (which might be on the next day)
            val sleepTimeDate = if (avgSleepTime.isBefore(finalWakeUpTime)) {
                dateAsJava.plusDays(1)
            } else {
                dateAsJava
            }
            
            val dayEnd = sleepTimeDate.atTime(avgSleepTime)
                .atZone(zoneId)
                .toInstant()
                .toKotlinInstant()
            
            return DayBounds(start = dayStart, end = dayEnd)
            
        } catch (e: Exception) {
            Napier.e("Error getting sleep data", e)
            return getDefaultDayBounds(date, timeZone)
        }
    }
    
    override suspend fun isHealthConnectAvailable(): Boolean {
        return try {
            val availability = HealthConnectClient.getSdkStatus(context)
            availability == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Napier.e("Error checking Health Connect availability", e)
            false
        }
    }
    
    override suspend fun hasSleepPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        
        return try {
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            grantedPermissions.containsAll(sleepPermissions)
        } catch (e: Exception) {
            Napier.e("Error checking sleep permissions", e)
            false
        }
    }
    
    override suspend fun requestSleepPermissions(): Boolean {
        // This would normally launch the permission request flow
        // which requires an Activity context and result handling
        // In a real implementation, this would be handled by a UI component
        Napier.w("Permission request flow should be handled by a UI component")
        return false
    }
    
    private suspend fun readSleepSessions(start: Instant, end: Instant): List<SleepSessionRecord> {
        val client = healthConnectClient ?: return emptyList()
        
        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                start.toJavaInstant(),
                end.toJavaInstant()
            )
            
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            
            val response = client.readRecords(request)
            response.records
        } catch (e: Exception) {
            Napier.e("Error reading sleep sessions", e)
            emptyList()
        }
    }
    
    private fun calculateAverageTime(times: List<java.time.LocalTime>): java.time.LocalTime? {
        if (times.isEmpty()) return null
        
        // Convert all times to minutes since midnight
        val minutesSinceMidnight = times.map { 
            it.toSecondOfDay() / 60
        }
        
        // Calculate average
        val avgMinutes = minutesSinceMidnight.average().toInt()
        
        // Convert back to LocalTime
        val hours = avgMinutes / 60
        val minutes = avgMinutes % 60
        
        return java.time.LocalTime.of(hours, minutes)
    }
    
    /**
     * Finds an early wake-up time by calculating the 15th percentile of wake-up times.
     * This helps account for users who occasionally wake up earlier than their normal pattern.
     */
    private fun findEarlyPercentile(times: List<java.time.LocalTime>): java.time.LocalTime? {
        if (times.size < 3) return null  // Need at least a few data points
        
        // Convert all times to minutes since midnight
        val minutesSinceMidnight = times.map { 
            it.toSecondOfDay() / 60
        }.sorted()
        
        // Calculate 15th percentile (captures early wake-ups without being extreme)
        val index = (minutesSinceMidnight.size * 0.15).toInt().coerceAtLeast(0)
        val earlyMinutes = minutesSinceMidnight[index]
        
        // Convert back to LocalTime
        val hours = earlyMinutes / 60
        val minutes = earlyMinutes % 60
        
        return java.time.LocalTime.of(hours, minutes)
    }
    
    private fun getDefaultDayBounds(date: LocalDate, timeZone: TimeZone): DayBounds {
        // Default day is 5am to midnight (next day) to accommodate early risers
        val zoneId = ZoneId.of(timeZone.id)
        val javaDate = date.toJavaLocalDate()
        
        val dayStart = javaDate.atTime(5, 0)
            .atZone(zoneId)
            .toInstant()
            .toKotlinInstant()
        
        val nextDay = javaDate.plusDays(1)
        val dayEnd = nextDay.atStartOfDay()
            .atZone(zoneId)
            .toInstant()
            .toKotlinInstant()
        
        return DayBounds(start = dayStart, end = dayEnd)
    }
}