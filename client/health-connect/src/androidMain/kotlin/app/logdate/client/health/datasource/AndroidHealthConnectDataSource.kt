package app.logdate.client.health.datasource

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.logdate.client.health.SleepSessionConstants
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.SleepStage
import app.logdate.client.health.model.SleepStageType
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.ZoneId
import java.util.UUID

/**
 * Android implementation of [RemoteHealthDataSource] using Health Connect API.
 */
class AndroidHealthConnectDataSource(
    private val context: Context
) : RemoteHealthDataSource {

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

    override suspend fun isAvailable(): Boolean {
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
        // which requires an Activity context and result handling.
        // In a real implementation, this would be handled by a UI component.
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
            
            // Map Health Connect sleep sessions to our model
            response.records.map { record ->
                SleepSession(
                    id = record.metadata.id,
                    startTime = record.startTime.toKotlinInstant(),
                    endTime = record.endTime.toKotlinInstant(),
                    sourceAppName = record.metadata.dataOrigin.packageName,
                    stages = record.stages.map { stage ->
                        SleepStage(
                            type = SleepStageType.UNKNOWN, // Use UNKNOWN for now as we can't access the real values
                            startTime = stage.startTime.toKotlinInstant(),
                            endTime = stage.endTime.toKotlinInstant()
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Napier.e("Error reading sleep sessions", e)
            emptyList()
        }
    }

    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (!isAvailable() || !hasSleepPermissions()) {
            return null
        }
        
        try {
            // Calculate the start and end times for the query
            val endTime = Clock.System.now()
            val startTime = Instant.fromEpochMilliseconds(
                endTime.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
            )
            
            // Get sleep sessions for the period
            val sleepSessions = getSleepSessions(startTime, endTime)
            
            if (sleepSessions.isEmpty()) {
                return null
            }
            
            // Extract wake-up times (end times of sleep sessions)
            val wakeUpTimes = sleepSessions.map { session ->
                val zoneId = ZoneId.of(timeZone.id)
                val dateTime = session.endTime.toJavaInstant().atZone(zoneId).toLocalTime()
                
                // Convert to hour and minute
                dateTime.hour to dateTime.minute
            }
            
            // Calculate average wake-up time
            val totalMinutes = wakeUpTimes.sumOf { (hour, minute) -> 
                hour * 60 + minute 
            }
            val avgMinutes = totalMinutes / wakeUpTimes.size
            
            val avgHour = avgMinutes / 60
            val avgMinute = avgMinutes % 60
            
            return TimeOfDay(avgHour, avgMinute)
            
        } catch (e: Exception) {
            Napier.e("Error calculating average wake-up time", e)
            return null
        }
    }

    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (!isAvailable() || !hasSleepPermissions()) {
            return null
        }
        
        try {
            // Calculate the start and end times for the query
            val endTime = Clock.System.now()
            val startTime = Instant.fromEpochMilliseconds(
                endTime.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
            )
            
            // Get sleep sessions for the period
            val sleepSessions = getSleepSessions(startTime, endTime)
            
            if (sleepSessions.isEmpty()) {
                return null
            }
            
            // Extract sleep times (start times of sleep sessions)
            val sleepTimes = sleepSessions.map { session ->
                val zoneId = ZoneId.of(timeZone.id)
                val dateTime = session.startTime.toJavaInstant().atZone(zoneId).toLocalTime()
                
                // Convert to hour and minute
                dateTime.hour to dateTime.minute
            }
            
            // Calculate average sleep time
            val totalMinutes = sleepTimes.sumOf { (hour, minute) -> 
                hour * 60 + minute 
            }
            val avgMinutes = totalMinutes / sleepTimes.size
            
            val avgHour = avgMinutes / 60
            val avgMinute = avgMinutes % 60
            
            return TimeOfDay(avgHour, avgMinute)
            
        } catch (e: Exception) {
            Napier.e("Error calculating average sleep time", e)
            return null
        }
    }
    
    /**
     * Maps Health Connect sleep stage to our model's sleep stage type.
     */
    private fun mapSleepStageType(stage: String): SleepStageType {
        return when (stage) {
            // Using our constants instead of the Health Connect API constants
            // This is needed because we can't directly access the Health Connect API constants in our compilation environment
            "${SleepSessionConstants.STAGE_TYPE_AWAKE}" -> SleepStageType.AWAKE
            "${SleepSessionConstants.STAGE_TYPE_DEEP}" -> SleepStageType.DEEP
            "${SleepSessionConstants.STAGE_TYPE_LIGHT}" -> SleepStageType.LIGHT
            "${SleepSessionConstants.STAGE_TYPE_REM}" -> SleepStageType.REM
            else -> SleepStageType.UNKNOWN
        }
    }
}