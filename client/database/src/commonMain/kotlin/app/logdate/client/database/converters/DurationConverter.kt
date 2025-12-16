package app.logdate.client.database.converters

import androidx.room.TypeConverter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Room type converter for Kotlin Duration.
 * 
 * This converter allows Room to store Duration objects in the database
 * by converting them to and from milliseconds stored as Long values.
 */
class DurationConverter {
    /**
     * Converts a Duration to a Long representing milliseconds for storage in the database.
     * 
     * @param value The Duration to convert
     * @return The Duration as milliseconds in a Long, or null if the input is null
     */
    @TypeConverter
    fun fromDuration(value: Duration?): Long? {
        return value?.inWholeMilliseconds
    }
    
    /**
     * Converts a Long representing milliseconds to a Duration.
     * 
     * @param value The milliseconds as a Long
     * @return The reconstructed Duration, or null if the input is null
     */
    @TypeConverter
    fun toDuration(value: Long?): Duration? {
        return value?.milliseconds
    }
}