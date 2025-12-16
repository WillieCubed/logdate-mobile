package app.logdate.client.database.entities.media

/**
 * Data class representing location data for media entities.
 * 
 * This class is designed to be embedded in media entities using Room's @Embedded annotation.
 * It encapsulates location coordinates for media items that have geolocation metadata.
 */
data class LocationData(
    /**
     * Latitude coordinate.
     */
    val latitude: Double,
    
    /**
     * Longitude coordinate.
     */
    val longitude: Double
)