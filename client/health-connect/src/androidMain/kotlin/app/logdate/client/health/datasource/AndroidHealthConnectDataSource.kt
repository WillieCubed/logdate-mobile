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
import kotlinx.datetime.TimeZone
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Android implementation of [RemoteHealthDataSource] using Health Connect API.
 */
class AndroidHealthConnectDataSource(
    private val context: Context,
) : RemoteHealthDataSource {
    private val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Napier.e("Failed to create HealthConnectClient", e)
            null
        }
    }

    private val sleepPermissions =
        setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

    override suspend fun isAvailable(): Boolean =
        try {
            val availability = HealthConnectClient.getSdkStatus(context)
            availability == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Napier.e("Error checking Health Connect availability", e)
            false
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

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> {
        val client = healthConnectClient ?: return emptyList()

        return try {
            val timeRangeFilter =
                TimeRangeFilter.between(
                    start.toJavaTimeInstant(),
                    end.toJavaTimeInstant(),
                )

            val request =
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRangeFilter,
                )

            val response = client.readRecords(request)

            response.records.map { record ->
                SleepSession(
                    id = record.metadata.id,
                    startTime = record.startTime.toKotlinxInstant(),
                    endTime = record.endTime.toKotlinxInstant(),
                    sourceAppName = record.metadata.dataOrigin.packageName,
                    stages =
                        record.stages.map { stage ->
                            SleepStage(
                                type = SleepStageType.UNKNOWN,
                                startTime = stage.startTime.toKotlinxInstant(),
                                endTime = stage.endTime.toKotlinxInstant(),
                            )
                        },
                )
            }
        } catch (e: Exception) {
            Napier.e("Error reading sleep sessions", e)
            emptyList()
        }
    }

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        if (!isAvailable() || !hasSleepPermissions()) {
            return null
        }

        try {
            // Calculate the start and end times for the query
            val endTime = Clock.System.now()
            val startTime =
                Instant.fromEpochMilliseconds(
                    endTime.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L),
                )

            // Get sleep sessions for the period
            val sleepSessions = getSleepSessions(startTime, endTime)

            if (sleepSessions.isEmpty()) {
                return null
            }

            val wakeUpTimes =
                sleepSessions.map { session ->
                    val zoneId = ZoneId.of(timeZone.id)
                    val dateTime =
                        session.endTime
                            .toJavaTimeInstant()
                            .atZone(zoneId)
                            .toLocalTime()

                    // Convert to hour and minute
                    dateTime.hour to dateTime.minute
                }

            // Calculate average wake-up time
            val totalMinutes =
                wakeUpTimes.sumOf { (hour, minute) ->
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

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? {
        if (!isAvailable() || !hasSleepPermissions()) {
            return null
        }

        try {
            // Calculate the start and end times for the query
            val endTime = Clock.System.now()
            val startTime =
                Instant.fromEpochMilliseconds(
                    endTime.toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L),
                )

            // Get sleep sessions for the period
            val sleepSessions = getSleepSessions(startTime, endTime)

            if (sleepSessions.isEmpty()) {
                return null
            }

            val sleepTimes =
                sleepSessions.map { session ->
                    val zoneId = ZoneId.of(timeZone.id)
                    val dateTime =
                        session.startTime
                            .toJavaTimeInstant()
                            .atZone(zoneId)
                            .toLocalTime()

                    // Convert to hour and minute
                    dateTime.hour to dateTime.minute
                }

            // Calculate average sleep time
            val totalMinutes =
                sleepTimes.sumOf { (hour, minute) ->
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

    private fun mapSleepStageType(stage: String): SleepStageType =
        when (stage) {
            "${SleepSessionConstants.STAGE_TYPE_AWAKE}" -> SleepStageType.AWAKE
            "${SleepSessionConstants.STAGE_TYPE_DEEP}" -> SleepStageType.DEEP
            "${SleepSessionConstants.STAGE_TYPE_LIGHT}" -> SleepStageType.LIGHT
            "${SleepSessionConstants.STAGE_TYPE_REM}" -> SleepStageType.REM
            else -> SleepStageType.UNKNOWN
        }
}

private fun Instant.toJavaTimeInstant(): java.time.Instant = java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

private fun java.time.Instant.toKotlinxInstant(): Instant = Instant.fromEpochSeconds(epochSecond, nano.toLong())
