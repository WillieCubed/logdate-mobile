package app.logdate.client.database.entities.media

/**
 * Data class representing the dimensions of a media item.
 * 
 * This class is designed to be embedded in media entities using Room's @Embedded annotation.
 * It encapsulates width and height information for media items.
 */
data class MediaDimensions(
    /**
     * Width in pixels.
     */
    val width: Int,
    
    /**
     * Height in pixels.
     */
    val height: Int
)