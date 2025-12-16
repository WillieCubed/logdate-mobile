package app.logdate.client.database.converters

import androidx.room.TypeConverter
import app.logdate.client.database.entities.media.LocationData

/**
 * Room type converter for LocationData.
 * 
 * This converter allows Room to store LocationData objects in the database
 * by converting them to and from a string representation with format "latitude,longitude".
 */
class LocationDataConverter {
    /**
     * Converts LocationData to a String for storage in the database.
     * 
     * @param location The LocationData to convert
     * @return The location as a string in format "latitude,longitude", or null if input is null
     */
    @TypeConverter
    fun fromLocationData(location: LocationData?): String? {
        return location?.let { "${it.latitude},${it.longitude}" }
    }
    
    /**
     * Converts a String to LocationData.
     * 
     * @param value The string representation in format "latitude,longitude"
     * @return The reconstructed LocationData, or null if input is null
     */
    @TypeConverter
    fun toLocationData(value: String?): LocationData? {
        return value?.let {
            val parts = it.split(",")
            if (parts.size == 2) {
                try {
                    LocationData(
                        latitude = parts[0].toDouble(),
                        longitude = parts[1].toDouble()
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