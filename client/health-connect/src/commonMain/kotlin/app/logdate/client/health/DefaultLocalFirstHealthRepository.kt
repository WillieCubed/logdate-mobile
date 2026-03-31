package app.logdate.client.health

import app.logdate.client.health.datasource.LocalHealthDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.health.util.LogdatePreferencesDataSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Default implementation of [LocalFirstHealthRepository] that follows the local-first pattern.
 *
 * This implementation tries to fetch data from the local cache first, and only if that fails
 * or the data is not available, it fetches from the remote source and caches the result.
 */
class DefaultLocalFirstHealthRepository(
    private val localDataSource: LocalHealthDataSource,
    private val remoteDataSource: RemoteHealthDataSource,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalFirstHealthRepository {
    // Implementation of HealthDataRepository methods
    override suspend fun getAvailableDataTypes(): List<String> =
        withContext(ioDispatcher) {
            if (remoteDataSource.isAvailable()) {
                try {
                    // Not all data sources will have this capability
                    // Default to an empty list if not supported
                    emptyList()
                } catch (e: Exception) {
                    Napier.e("Error getting available data types", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    override suspend fun isHealthDataAvailable(): Boolean =
        withContext(ioDispatcher) {
            remoteDataSource.isAvailable()
        }

    override suspend fun hasSleepPermissions(): Boolean =
        withContext(ioDispatcher) {
            remoteDataSource.hasSleepPermissions()
        }

    override suspend fun requestSleepPermissions(): Boolean =
        withContext(ioDispatcher) {
            remoteDataSource.requestSleepPermissions()
        }

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> =
        withContext(ioDispatcher) {
            try {
                // Try to get from local cache first
                val localSessions = localDataSource.getSleepSessions(start, end)
                if (localSessions.isNotEmpty()) {
                    Napier.d("Returning ${localSessions.size} sleep sessions from local cache")
                    return@withContext localSessions
                }

                // If not in cache or empty, try remote
                if (remoteDataSource.isAvailable() && remoteDataSource.hasSleepPermissions()) {
                    Napier.d("Fetching sleep sessions from remote data source")
                    val remoteSessions = remoteDataSource.getSleepSessions(start, end)

                    // Cache the result
                    if (remoteSessions.isNotEmpty()) {
                        Napier.d("Caching ${remoteSessions.size} sleep sessions")
                        localDataSource.storeSleepSessions(remoteSessions)
                    }

                    return@withContext remoteSessions
                }

                // If remote is not available, return empty list
                Napier.d("Remote data source not available, returning empty list")
                emptyList()
            } catch (e: Exception) {
                Napier.e("Error getting sleep sessions", e)
                emptyList()
            }
        }

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? =
        withContext(ioDispatcher) {
            try {
                // Try to get from local cache first
                val localWakeUpTime = localDataSource.getAverageWakeUpTime(timeZone)
                if (localWakeUpTime != null) {
                    Napier.d("Returning average wake-up time from local cache: $localWakeUpTime")
                    return@withContext localWakeUpTime
                }

                // If not in cache, try remote
                if (remoteDataSource.isAvailable() && remoteDataSource.hasSleepPermissions()) {
                    Napier.d("Fetching average wake-up time from remote data source")
                    val remoteWakeUpTime = remoteDataSource.getAverageWakeUpTime(timeZone, days)

                    // Cache the result
                    if (remoteWakeUpTime != null) {
                        Napier.d("Caching average wake-up time: $remoteWakeUpTime")
                        localDataSource.storeAverageWakeUpTime(timeZone, remoteWakeUpTime)
                    }

                    return@withContext remoteWakeUpTime
                }

                // If remote is not available, return null
                Napier.d("Remote data source not available, returning null for average wake-up time")
                null
            } catch (e: Exception) {
                Napier.e("Error getting average wake-up time", e)
                null
            }
        }

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? =
        withContext(ioDispatcher) {
            try {
                // Try to get from local cache first
                val localSleepTime = localDataSource.getAverageSleepTime(timeZone)
                if (localSleepTime != null) {
                    Napier.d("Returning average sleep time from local cache: $localSleepTime")
                    return@withContext localSleepTime
                }

                // If not in cache, try remote
                if (remoteDataSource.isAvailable() && remoteDataSource.hasSleepPermissions()) {
                    Napier.d("Fetching average sleep time from remote data source")
                    val remoteSleepTime = remoteDataSource.getAverageSleepTime(timeZone, days)

                    // Cache the result
                    if (remoteSleepTime != null) {
                        Napier.d("Caching average sleep time: $remoteSleepTime")
                        localDataSource.storeAverageSleepTime(timeZone, remoteSleepTime)
                    }

                    return@withContext remoteSleepTime
                }

                // If remote is not available, return null
                Napier.d("Remote data source not available, returning null for average sleep time")
                null
            } catch (e: Exception) {
                Napier.e("Error getting average sleep time", e)
                null
            }
        }

    /**
     * ## Fallback cascade
     *
     * 1. **Per-day sleep data** (if [sleepBasedBoundariesEnabled]): actual sleep
     *    sessions that bracket this date. Day starts at wake-up, ends at bedtime.
     * 2. **User preferences**: configured `dayStartHour` / `dayEndHour`.
     * 3. **Default**: [DEFAULT_DAY_START_HOUR] to next-day [DEFAULT_DAY_START_HOUR].
     */
    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds =
        withContext(ioDispatcher) {
            try {
                if (sleepBasedBoundariesEnabled) {
                    val perDayBounds = getPerDaySleepBounds(date, timeZone)
                    if (perDayBounds != null) {
                        return@withContext perDayBounds
                    }
                }
                getUserPreferredBounds(date, timeZone)
            } catch (e: Exception) {
                Napier.e("Error getting day bounds", e)
                getDefaultDayBounds(date, timeZone)
            }
        }

    /**
     * Determines day bounds from actual per-day sleep session data.
     *
     * Uses a single query spanning D-1 18:00 to D+1 14:00, then splits results
     * into wake-up candidates (ending before D 14:00) and bedtime candidates
     * (starting after D 14:00). Each boundary falls back independently.
     */
    private suspend fun getPerDaySleepBounds(
        date: LocalDate,
        timeZone: TimeZone,
    ): DayBounds? {
        if (!hasSleepPermissions()) {
            Napier.d("Sleep permissions not granted, skipping per-day sleep bounds")
            return null
        }

        val previousDay = date.minus(1, DateTimeUnit.DAY)
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val windowStart = LocalDateTime(previousDay, LocalTime(18, 0)).toInstant(timeZone)
        val windowEnd = LocalDateTime(nextDay, LocalTime(14, 0)).toInstant(timeZone)
        val allSessions = getSleepSessions(windowStart, windowEnd)

        val midday = LocalDateTime(date, LocalTime(14, 0)).toInstant(timeZone)
        val wakeUpSession = findPrimarySleepSession(allSessions.filter { it.endTime <= midday })
        val bedtimeSession = findPrimarySleepSession(allSessions.filter { it.startTime >= midday })

        val dayStart = wakeUpSession?.endTime
        val dayEnd = bedtimeSession?.startTime

        if (dayStart == null && dayEnd == null) {
            return null
        }

        val fallbackBounds = getUserPreferredBounds(date, timeZone)
        return DayBounds(
            start = dayStart ?: fallbackBounds.start,
            end = dayEnd ?: fallbackBounds.end,
        )
    }

    /**
     * Selects the primary sleep session from a list, filtering out naps.
     * Sessions of 3+ hours are primary candidates; the longest wins.
     */
    private fun findPrimarySleepSession(sessions: List<SleepSession>): SleepSession? {
        if (sessions.isEmpty()) return null

        fun SleepSession.duration() = endTime - startTime
        val longSessions = sessions.filter { it.duration() >= 3.hours }
        return (longSessions.ifEmpty { sessions }).maxByOrNull { it.duration() }
    }

    /**
     * Gets day bounds from user preferences, falling back to [DEFAULT_DAY_START_HOUR].
     */
    private suspend fun getUserPreferredBounds(
        date: LocalDate,
        timeZone: TimeZone,
    ): DayBounds {
        val preferences = preferencesDataSource.getPreferences()
        val startHour = preferences.dayStartHour ?: DEFAULT_DAY_START_HOUR
        val endHour = preferences.dayEndHour

        val dayStart = LocalDateTime(date, LocalTime(startHour, 0)).toInstant(timeZone)

        if (endHour != null) {
            val endDate = if (endHour < startHour) date.plus(1, DateTimeUnit.DAY) else date
            val dayEnd = LocalDateTime(endDate, LocalTime(endHour, 0)).toInstant(timeZone)
            return DayBounds(start = dayStart, end = dayEnd)
        }

        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val dayEnd = LocalDateTime(nextDay, LocalTime(startHour, 0)).toInstant(timeZone)
        return DayBounds(start = dayStart, end = dayEnd)
    }

    /**
     * Last-resort fallback using [DEFAULT_DAY_START_HOUR].
     */
    private fun getDefaultDayBounds(
        date: LocalDate,
        timeZone: TimeZone,
    ): DayBounds {
        val dayStart = LocalDateTime(date, LocalTime(DEFAULT_DAY_START_HOUR, 0)).toInstant(timeZone)
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val dayEnd = LocalDateTime(nextDay, LocalTime(DEFAULT_DAY_START_HOUR, 0)).toInstant(timeZone)
        return DayBounds(start = dayStart, end = dayEnd)
    }

    companion object {
        /**
         * Initial default hour for day boundaries when no sleep data or user
         * preference exists. Configurable in Settings > Timeline > Day boundaries.
         */
        const val DEFAULT_DAY_START_HOUR = 4
    }
}
