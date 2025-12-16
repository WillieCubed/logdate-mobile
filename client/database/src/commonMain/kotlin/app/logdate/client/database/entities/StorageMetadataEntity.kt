package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Storage metadata for content objects managed by the app.
 * This table tracks the actual storage footprint of content that counts toward quota.
 */
@Entity(
    tableName = "storage_metadata"
)
data class StorageMetadataEntity(
    /**
     * The UUID of the content object this storage metadata refers to.
     * This should match the primary key of a note entity (TextNoteEntity.uid, ImageNoteEntity.uid, etc.)
     */
    @PrimaryKey
    val contentId: Uuid,
    
    /**
     * The type of content object this metadata describes.
     */
    val contentType: StorageContentType,
    
    /**
     * The actual file size in bytes for this content.
     * For media files (images, videos, audio), this is the file size.
     * For text content, this can be the UTF-16 byte count.
     */
    val sizeBytes: Long,
    
    /**
     * URI or path to the content, for reference.
     */
    val contentUri: String,
    
    /**
     * When this storage metadata was first recorded.
     */
    val recordedAt: Instant,
    
    /**
     * When this storage metadata was last updated.
     */
    val lastUpdated: Instant,
    
    /**
     * Whether this content should be excluded from quota tracking.
     * Content marked as excluded will not count toward storage quotas.
     */
    val excludeFromQuota: Boolean = false,
)

/**
 * Types of content that have storage metadata tracked.
 */
enum class StorageContentType {
    TEXT_NOTE,
    IMAGE_NOTE,
    VIDEO_NOTE,
    VOICE_NOTE,
    JOURNAL_METADATA,
    USER_PROFILE,
    ATTACHMENT
}