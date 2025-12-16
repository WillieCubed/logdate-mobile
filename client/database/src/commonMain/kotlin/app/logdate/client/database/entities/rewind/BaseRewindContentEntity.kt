package app.logdate.client.database.entities.rewind

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Base class for all rewind content entities.
 * 
 * This abstract class defines the common fields that all rewind content
 * entities share. Specific content types (text, image, video) extend this
 * class with their own specific fields.
 */
abstract class BaseRewindContentEntity {
    /**
     * Unique identifier for this content item.
     * 
     * Note: This is re-annotated in each subclass because Room
     * doesn't properly recognize @PrimaryKey in abstract classes.
     */
    abstract val id: Uuid
    
    /**
     * Reference to the parent rewind.
     */
    abstract val rewindId: Uuid
    
    /**
     * ID of the original source content (e.g., journal note ID, media ID).
     */
    abstract val sourceId: Uuid
    
    /**
     * When this content was created.
     */
    abstract val timestamp: Instant
}