package app.logdate.client.database.converters

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Converts date-like objects into a format that can be stored in the database.
 */
object TimestampConverter {
    /**
     * Converts a [Instant] to a [Long] that can be stored in a Room database.
     */
    @TypeConverter
    fun fromInstant(value: Instant): Long {
        return value.toEpochMilliseconds()
    }

    /**
     * Converts a [Long] timestamp to a [Instant] usable for domain layer operations.
     */
    @TypeConverter
    fun toInstant(value: Long): Instant {
        return Instant.fromEpochMilliseconds(value)
    }
}