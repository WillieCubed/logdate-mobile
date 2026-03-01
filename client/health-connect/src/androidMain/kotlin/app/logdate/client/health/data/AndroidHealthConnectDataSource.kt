package app.logdate.client.health.data

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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import java.time.ZoneId

/**
 * Android implementation of RemoteHealthDataSource using Health Connect API.
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

    override suspend fun isHealthApiAvailable(): Boolean {
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

    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        val client = healthConnectClient ?: return emptyList()
        
        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                start.toJavaTimeInstant(),
                end.toJavaTimeInstant()
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
            val end = Clock.System.now()
            val start = Instant.fromEpochMilliseconds(
                end.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
            )

            val sessions = getSleepSessions(start, end)

            if (sessions.isEmpty()) {
                Napier.d("No sleep data available")
                return null
            }

            val wakeUpTimes = mutableListOf<java.time.LocalTime>()
            val zoneId = ZoneId.of(timeZone.id)

            sessions.forEach { session ->
                val endDateTime = session.endTime.toJavaTimeInstant().atZone(zoneId)
                wakeUpTimes.add(endDateTime.toLocalTime())
            }

            val javaTime = calculateAverageTime(wakeUpTimes) ?: return null

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
            val end = Clock.System.now()
            val start = Instant.fromEpochMilliseconds(
                end.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
            )

            val sessions = getSleepSessions(start, end)

            if (sessions.isEmpty()) {
                Napier.d("No sleep data available")
                return null
            }

            val sleepTimes = mutableListOf<java.time.LocalTime>()
            val zoneId = ZoneId.of(timeZone.id)

            sessions.forEach { session ->
                val startDateTime = session.startTime.toJavaTimeInstant().atZone(zoneId)
                sleepTimes.add(startDateTime.toLocalTime())
            }

            val javaTime = calculateAverageTime(sleepTimes) ?: return null

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

    override suspend fun getAvailableDataTypes(): List<String> {
        val client = healthConnectClient ?: return emptyList()
        
        return try {
            val availableTypes = client.permissionController.getGrantedPermissions()
            availableTypes.toList()
        } catch (e: Exception) {
            Napier.e("Error getting available data types", e)
            emptyList()
        }
    }

    private fun SleepSessionRecord.toSleepSession(): SleepSession {
        return SleepSession(
            id = metadata.id,
            startTime = startTime.toKotlinxInstant(),
            endTime = endTime.toKotlinxInstant(),
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
                    startTime = stage.startTime.toKotlinxInstant(),
                    endTime = stage.endTime.toKotlinxInstant()
                )
            }
        )
    }

    private fun calculateAverageTime(times: List<java.time.LocalTime>): java.time.LocalTime? {
        if (times.isEmpty()) return null

        val secondsSinceMidnight = times.map { it.toSecondOfDay() }
        val avgSeconds = secondsSinceMidnight.average().toInt()
        val hours = avgSeconds / 3600
        val minutes = (avgSeconds % 3600) / 60
        val seconds = avgSeconds % 60

        return java.time.LocalTime.of(hours, minutes, seconds)
    }
}

private fun Instant.toJavaTimeInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

private fun java.time.Instant.toKotlinxInstant(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano.toLong())
