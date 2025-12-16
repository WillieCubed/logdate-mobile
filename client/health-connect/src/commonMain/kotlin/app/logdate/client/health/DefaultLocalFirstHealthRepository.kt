package app.logdate.client.health

import app.logdate.client.health.util.LogdatePreferencesDataSource
import app.logdate.client.health.util.UserPreferences
import app.logdate.client.health.datasource.LocalHealthDataSource
import app.logdate.client.health.datasource.RemoteHealthDataSource
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext

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
    private val ioDispatcher: CoroutineContext
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
            localDataSource.isAvailable() || remoteDataSource.isAvailable()
        }

    override suspend fun hasSleepPermissions(): Boolean = 
        withContext(ioDispatcher) {
            remoteDataSource.hasSleepPermissions()
        }

    override suspend fun requestSleepPermissions(): Boolean = 
        withContext(ioDispatcher) {
            remoteDataSource.requestSleepPermissions()
        }

    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> =
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

    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? =
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

    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? =
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

    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds =
        withContext(ioDispatcher) {
            try {
                // Try to determine bounds from sleep data
                val sleepBasedBounds = getSleepBasedBounds(date, timeZone)
                if (sleepBasedBounds != null) {
                    return@withContext sleepBasedBounds
                }
                
                // Fall back to user preferences or default bounds
                getUserPreferredBounds(date, timeZone)
            } catch (e: Exception) {
                Napier.e("Error getting day bounds", e)
                getDefaultDayBounds(date, timeZone)
            }
        }
    
    /**
     * Attempts to determine day bounds from sleep data.
     */
    private suspend fun getSleepBasedBounds(date: LocalDate, timeZone: TimeZone): DayBounds? {
        if (!hasSleepPermissions()) {
            Napier.d("Sleep permissions not granted, using default day bounds")
            return null
        }
        
        try {
            // Check if we have average sleep/wake times
            val avgWakeUpTime = getAverageWakeUpTime(timeZone)
            val avgSleepTime = getAverageSleepTime(timeZone)
            
            // If no valid averages, use default bounds
            if (avgWakeUpTime == null || avgSleepTime == null) {
                return null
            }
            
            // Use the earlier of avgWakeUpTime or avgWakeUpTime - 1 hour (with a minimum of 4am)
            val wakeUpTimeToUse = avgWakeUpTime.minusHours(1)
            
            // Ensure we don't set the start too early (before 4am)
            val fourAM = TimeOfDay.of(4, 0)
            val finalWakeUpTime = if (wakeUpTimeToUse.isBefore(fourAM)) fourAM else wakeUpTimeToUse
            
            // Day starts at adjusted wake-up time
            val startDateTime = LocalDateTime(
                date, 
                LocalTime(finalWakeUpTime.hour, finalWakeUpTime.minute, finalWakeUpTime.second)
            )
            val dayStart = startDateTime.toInstant(timeZone)
            
            // Day ends at sleep time (which might be on the next day)
            val sleepDate = if (avgSleepTime.isBefore(finalWakeUpTime)) {
                date.plus(1, DateTimeUnit.DAY)
            } else {
                date
            }
            
            val endDateTime = LocalDateTime(
                sleepDate,
                LocalTime(avgSleepTime.hour, avgSleepTime.minute, avgSleepTime.second)
            )
            val dayEnd = endDateTime.toInstant(timeZone)
            
            return DayBounds(start = dayStart, end = dayEnd)
            
        } catch (e: Exception) {
            Napier.e("Error getting sleep data", e)
            return null
        }
    }
    
    /**
     * Gets day bounds based on user preferences, or defaults to reasonable hours.
     */
    private suspend fun getUserPreferredBounds(date: LocalDate, timeZone: TimeZone): DayBounds {
        // Check for user preferences first
        val preferences = preferencesDataSource.getPreferences()
        
        // If user has set specific preferences, use those
        val userDayStartHour = preferences.dayStartHour
        val userDayEndHour = preferences.dayEndHour
        
        if (userDayStartHour != null && userDayEndHour != null) {
            // Day starts at user's preferred hour
            val startDateTime = LocalDateTime(date, LocalTime(userDayStartHour, 0))
            val dayStart = startDateTime.toInstant(timeZone)
            
            // If end hour is before start hour, it must be for the next day
            // We know userDayStartHour and userDayEndHour are not null at this point
            val startHour = userDayStartHour ?: 5
            val endHour = userDayEndHour ?: 0
            
            val endDate = if (endHour < startHour) {
                date.plus(1, DateTimeUnit.DAY)
            } else {
                date
            }
            
            val endDateTime = LocalDateTime(endDate, LocalTime(userDayEndHour, 0))
            val dayEnd = endDateTime.toInstant(timeZone)
            
            return DayBounds(start = dayStart, end = dayEnd)
        }
        
        return getDefaultDayBounds(date, timeZone)
    }
    
    /**
     * Gets default day bounds (5am to midnight next day).
     */
    private fun getDefaultDayBounds(date: LocalDate, timeZone: TimeZone): DayBounds {
        // Default day is 5am to midnight (next day) to accommodate early risers
        val startOfDay = date.atStartOfDayIn(timeZone)
        val dayStart = Instant.fromEpochSeconds(startOfDay.epochSeconds + 5 * 60 * 60)
        
        // Default day ends at midnight (next day)
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val dayEnd = nextDay.atStartOfDayIn(timeZone)
        
        return DayBounds(start = dayStart, end = dayEnd)
    }
}