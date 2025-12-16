package app.logdate.client.health.datasource

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/**
 * In-memory implementation of [LocalHealthDataSource].
 * 
 * This implementation stores all data in memory and is intended for use
 * on all platforms as a simple caching mechanism. In a real application,
 * this would be replaced with a database-backed implementation.
 */
class InMemoryLocalHealthDataSource : LocalHealthDataSource {
    // Cache for sleep sessions, keyed by session ID
    private val sleepSessionsCache = mutableMapOf<String, SleepSession>()
    
    // Cache for average wake-up times, keyed by time zone ID
    private val averageWakeUpTimeCache = mutableMapOf<String, TimeOfDay>()
    
    // Cache for average sleep times, keyed by time zone ID
    private val averageSleepTimeCache = mutableMapOf<String, TimeOfDay>()
    
    // Last time the cache was updated, used for cache invalidation
    private var lastUpdateTime: Long = 0
    
    // Cache validity duration in milliseconds (12 hours)
    private val cacheValidityDuration: Long = 12 * 60 * 60 * 1000
    
    override suspend fun isAvailable(): Boolean = true
    
    override suspend fun storeSleepSessions(sessions: List<SleepSession>) {
        sessions.forEach { session ->
            sleepSessionsCache[session.id] = session
        }
        updateLastUpdateTime()
        Napier.d("Stored ${sessions.size} sleep sessions in memory cache")
    }
    
    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        if (isCacheInvalid()) {
            Napier.d("Sleep sessions cache is invalid, returning empty list")
            return emptyList()
        }
        
        return sleepSessionsCache.values
            .filter { 
                it.startTime >= start && it.endTime <= end 
            }
            .sortedBy { it.startTime }
            .also { 
                Napier.d("Retrieved ${it.size} sleep sessions from memory cache") 
            }
    }
    
    override suspend fun storeAverageWakeUpTime(timeZone: TimeZone, wakeUpTime: TimeOfDay) {
        averageWakeUpTimeCache[timeZone.id] = wakeUpTime
        updateLastUpdateTime()
        Napier.d("Stored average wake-up time in memory cache: $wakeUpTime for time zone ${timeZone.id}")
    }
    
    override suspend fun getAverageWakeUpTime(timeZone: TimeZone): TimeOfDay? {
        if (isCacheInvalid()) {
            Napier.d("Average wake-up time cache is invalid, returning null")
            return null
        }
        
        return averageWakeUpTimeCache[timeZone.id].also { 
            Napier.d("Retrieved average wake-up time from memory cache: $it for time zone ${timeZone.id}")
        }
    }
    
    override suspend fun storeAverageSleepTime(timeZone: TimeZone, sleepTime: TimeOfDay) {
        averageSleepTimeCache[timeZone.id] = sleepTime
        updateLastUpdateTime()
        Napier.d("Stored average sleep time in memory cache: $sleepTime for time zone ${timeZone.id}")
    }
    
    override suspend fun getAverageSleepTime(timeZone: TimeZone): TimeOfDay? {
        if (isCacheInvalid()) {
            Napier.d("Average sleep time cache is invalid, returning null")
            return null
        }
        
        return averageSleepTimeCache[timeZone.id].also { 
            Napier.d("Retrieved average sleep time from memory cache: $it for time zone ${timeZone.id}")
        }
    }
    
    override suspend fun clearCache() {
        sleepSessionsCache.clear()
        averageWakeUpTimeCache.clear()
        averageSleepTimeCache.clear()
        lastUpdateTime = 0
        Napier.d("Cleared memory cache")
    }
    
    /**
     * Updates the last update time to the current time.
     */
    private fun updateLastUpdateTime() {
        lastUpdateTime = Clock.System.now().toEpochMilliseconds()
    }
    
    /**
     * Checks if the cache is invalid (too old).
     */
    private fun isCacheInvalid(): Boolean {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val cacheAge = currentTime - lastUpdateTime
        return lastUpdateTime == 0L || cacheAge > cacheValidityDuration
    }
}