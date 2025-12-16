package app.logdate.client.database.converters

import androidx.room.TypeConverter
import app.logdate.client.database.entities.media.MediaDimensions

/**
 * Room type converter for MediaDimensions.
 * 
 * This converter allows Room to store MediaDimensions objects in the database
 * by converting them to and from a string representation with format "width,height".
 */
class MediaDimensionsConverter {
    /**
     * Converts MediaDimensions to a String for storage in the database.
     * 
     * @param dimensions The MediaDimensions to convert
     * @return The dimensions as a string in format "width,height", or null if input is null
     */
    @TypeConverter
    fun fromMediaDimensions(dimensions: MediaDimensions?): String? {
        return dimensions?.let { "${it.width},${it.height}" }
    }
    
    /**
     * Converts a String to MediaDimensions.
     * 
     * @param value The string representation in format "width,height"
     * @return The reconstructed MediaDimensions, or null if input is null
     */
    @TypeConverter
    fun toMediaDimensions(value: String?): MediaDimensions? {
        return value?.let {
            val parts = it.split(",")
            if (parts.size == 2) {
                try {
                    MediaDimensions(
                        width = parts[0].toInt(),
                        height = parts[1].toInt()
                    )
                } catch (e: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }
    }
}