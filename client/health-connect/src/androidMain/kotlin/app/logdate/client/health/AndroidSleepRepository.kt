package app.logdate.client.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.SleepStage
import app.logdate.client.health.model.SleepStageType
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Android implementation of SleepRepository using Health Connect API.
 */
class AndroidSleepRepository(
    private val context: Context
) : SleepRepository {

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

    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
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
            response.records.map { it.toSleepSession() }
        } catch (e: Exception) {
            Napier.e("Error reading sleep sessions", e)
            emptyList()
        }
    }

    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (!hasSleepPermissions()) {
            Napier.d("Sleep permissions not granted")
            return null
        }
        
        try {
            // Calculate time range for query
            val end = java.time.Instant.now()
            val start = end.minus(days.toLong(), ChronoUnit.DAYS)
            
            // Get sleep sessions
            val sessions = getSleepSessions(
                start.toKotlinInstant(),
                end.toKotlinInstant()
            )
            
            if (sessions.isEmpty()) {
                Napier.d("No sleep data available")
                return null
            }
            
            // Calculate average wake-up time
            val wakeUpTimes = mutableListOf<java.time.LocalTime>()
            val zoneId = ZoneId.of(timeZone.id)
            
            sessions.forEach { session ->
                val endDateTime = session.endTime.toJavaInstant().atZone(zoneId)
                wakeUpTimes.add(endDateTime.toLocalTime())
            }
            
            val javaTime = calculateAverageTime(wakeUpTimes) ?: return null
            
            // Convert to our TimeOfDay model
            return TimeOfDay(
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second
            )
            
        } catch (e: Exception) {
            Napier.e("Error calculating average wake-up time", e)
            return null
        }
    }

    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (!hasSleepPermissions()) {
            Napier.d("Sleep permissions not granted")
            return null
        }
        
        try {
            // Calculate time range for query
            val end = java.time.Instant.now()
            val start = end.minus(days.toLong(), ChronoUnit.DAYS)
            
            // Get sleep sessions
            val sessions = getSleepSessions(
                start.toKotlinInstant(),
                end.toKotlinInstant()
            )
            
            if (sessions.isEmpty()) {
                Napier.d("No sleep data available")
                return null
            }
            
            // Calculate average sleep time
            val sleepTimes = mutableListOf<java.time.LocalTime>()
            val zoneId = ZoneId.of(timeZone.id)
            
            sessions.forEach { session ->
                val startDateTime = session.startTime.toJavaInstant().atZone(zoneId)
                sleepTimes.add(startDateTime.toLocalTime())
            }
            
            val javaTime = calculateAverageTime(sleepTimes) ?: return null
            
            // Convert to our TimeOfDay model
            return TimeOfDay(
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second
            )
            
        } catch (e: Exception) {
            Napier.e("Error calculating average sleep time", e)
            return null
        }
    }

    // Helper method to convert Health Connect SleepSessionRecord to our model
    private fun SleepSessionRecord.toSleepSession(): SleepSession {
        return SleepSession(
            id = metadata.id,
            startTime = startTime.toKotlinInstant(),
            endTime = endTime.toKotlinInstant(),
            sourceAppName = metadata.dataOrigin.packageName,
            stages = stages.map { stage ->
                SleepStage(
                    type = when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStageType.AWAKE
                        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
                        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
                        SleepSessionRecord.STAGE_TYPE_UNKNOWN -> SleepStageType.UNKNOWN
                        else -> SleepStageType.UNKNOWN
                    },
                    startTime = stage.startTime.toKotlinInstant(),
                    endTime = stage.endTime.toKotlinInstant()
                )
            }
        )
    }

    // Helper method to calculate average time
    private fun calculateAverageTime(times: List<java.time.LocalTime>): java.time.LocalTime? {
        if (times.isEmpty()) return null
        
        // Convert all times to seconds since midnight
        val secondsSinceMidnight = times.map { 
            it.toSecondOfDay()
        }
        
        // Calculate average
        val avgSeconds = secondsSinceMidnight.average().toInt()
        
        // Convert back to LocalTime
        val hours = avgSeconds / 3600
        val minutes = (avgSeconds % 3600) / 60
        val seconds = avgSeconds % 60
        
        return java.time.LocalTime.of(hours, minutes, seconds)
    }
}