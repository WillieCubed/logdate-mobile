package app.logdate.client.health.data

import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * In-memory implementation of LocalHealthDataSource.
 * This stores health data in memory for the duration of the app session.
 * In a real app, this would be replaced with a database-backed implementation.
 */
class InMemoryLocalHealthDataSource : LocalHealthDataSource {
    // In-memory storage
    private val sleepSessions = mutableListOf<SleepSession>()
    private val dayBoundsMap = mutableMapOf<String, DayBounds>()
    
    override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> {
        return sleepSessions.filter { 
            (it.startTime >= start && it.startTime <= end) || 
            (it.endTime >= start && it.endTime <= end)
        }
    }
    
    override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (sleepSessions.isEmpty()) {
            return null
        }
        
        // Calculate from end times of sleep sessions
        val now = Clock.System.now()
        val daysInSeconds = days * 24 * 60 * 60L
        val startTime = Instant.fromEpochSeconds(now.epochSeconds - daysInSeconds)
        
        val recentSessions = sleepSessions.filter { it.endTime > startTime }
        if (recentSessions.isEmpty()) {
            return null
        }
        
        // Get wake-up times (end times of sleep sessions)
        val wakeUpTimes = recentSessions.map {
            val localDateTime = it.endTime.toLocalDateTime(timeZone)
            TimeOfDay(
                hour = localDateTime.hour,
                minute = localDateTime.minute,
                second = localDateTime.second
            )
        }
        
        // Calculate average
        var totalSeconds = 0
        wakeUpTimes.forEach {
            totalSeconds += (it.hour * 3600) + (it.minute * 60) + it.second
        }
        
        val averageSeconds = totalSeconds / wakeUpTimes.size
        val hours = averageSeconds / 3600
        val minutes = (averageSeconds % 3600) / 60
        val seconds = averageSeconds % 60
        
        return TimeOfDay(hours, minutes, seconds)
    }
    
    override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? {
        if (sleepSessions.isEmpty()) {
            return null
        }
        
        // Calculate from start times of sleep sessions
        val now = Clock.System.now()
        val daysInSeconds = days * 24 * 60 * 60L
        val startTime = Instant.fromEpochSeconds(now.epochSeconds - daysInSeconds)
        
        val recentSessions = sleepSessions.filter { it.startTime > startTime }
        if (recentSessions.isEmpty()) {
            return null
        }
        
        // Get sleep times (start times of sleep sessions)
        val sleepTimes = recentSessions.map {
            val localDateTime = it.startTime.toLocalDateTime(timeZone)
            TimeOfDay(
                hour = localDateTime.hour,
                minute = localDateTime.minute,
                second = localDateTime.second
            )
        }
        
        // Calculate average
        var totalSeconds = 0
        sleepTimes.forEach {
            totalSeconds += (it.hour * 3600) + (it.minute * 60) + it.second
        }
        
        val averageSeconds = totalSeconds / sleepTimes.size
        val hours = averageSeconds / 3600
        val minutes = (averageSeconds % 3600) / 60
        val seconds = averageSeconds % 60
        
        return TimeOfDay(hours, minutes, seconds)
    }
    
    override suspend fun saveSleepSessions(sessions: List<SleepSession>): Boolean {
        return try {
            sleepSessions.addAll(sessions)
            true
        } catch (e: Exception) {
            Napier.e("Failed to save sleep sessions", e)
            false
        }
    }
    
    override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
        val key = "${date.toString()}_${timeZone.id}"
        return dayBoundsMap[key] ?: throw IllegalStateException("No day bounds available for $date in $timeZone")
    }
    
    override suspend fun saveDayBoundsForDate(date: LocalDate, bounds: DayBounds, timeZone: TimeZone): Boolean {
        return try {
            val key = "${date.toString()}_${timeZone.id}"
            dayBoundsMap[key] = bounds
            true
        } catch (e: Exception) {
            Napier.e("Failed to save day bounds", e)
            false
        }
    }
}