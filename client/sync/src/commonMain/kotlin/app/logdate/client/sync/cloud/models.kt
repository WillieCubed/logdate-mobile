package app.logdate.client.sync.cloud

import kotlinx.serialization.Serializable

/**
 * Response indicating if a username is available.
 */
@Serializable
data class CheckUsernameAvailabilityResponse(
    val username: String,
    val available: Boolean
)

// Content Sync Models
@Serializable
data class ContentUploadRequest(
    val id: String,
    val type: String, // TEXT, IMAGE, VIDEO, AUDIO
    val content: String?, // For text notes
    val mediaUri: String?, // For media notes
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable

data class ContentUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable

data class ContentUpdateRequest(
    val content: String?,
    val mediaUri: String?,
    val lastUpdated: Long,
    val syncVersion: Long
)

@Serializable

data class ContentUpdateResponse(
    val id: String,
    val serverVersion: Long,
    val updatedAt: Long
)

@Serializable

data class ContentChangesResponse(
    val changes: List<ContentChange>,
    val deletions: List<ContentDeletion>,
    val lastTimestamp: Long
)

@Serializable

data class ContentChange(
    val id: String,
    val type: String,
    val content: String?,
    val mediaUri: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable

data class ContentDeletion(
    val id: String,
    val deletedAt: Long
)

// Journal Sync Models
@Serializable

data class JournalUploadRequest(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable

data class JournalUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable

data class JournalUpdateRequest(
    val title: String,
    val description: String,
    val lastUpdated: Long,
    val syncVersion: Long
)

@Serializable

data class JournalUpdateResponse(
    val id: String,
    val serverVersion: Long,
    val updatedAt: Long
)

@Serializable

data class JournalChangesResponse(
    val changes: List<JournalChange>,
    val deletions: List<JournalDeletion>,
    val lastTimestamp: Long
)

@Serializable

data class JournalChange(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable

data class JournalDeletion(
    val id: String,
    val deletedAt: Long
)

// Association Sync Models
@Serializable

data class AssociationUploadRequest(
    val associations: List<Association>
)

@Serializable

data class Association(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val syncVersion: Long = 0
)

@Serializable

data class AssociationUploadResponse(
    val uploadedCount: Int,
    val uploadedAt: Long
)

@Serializable

data class AssociationChangesResponse(
    val changes: List<AssociationChange>,
    val deletions: List<AssociationDeletion>,
    val lastTimestamp: Long
)

@Serializable

data class AssociationChange(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable

data class AssociationDeletion(
    val journalId: String,
    val contentId: String,
    val deletedAt: Long
)

@Serializable

data class AssociationDeleteRequest(
    val associations: List<AssociationDeleteItem>
)

@Serializable

data class AssociationDeleteItem(
    val journalId: String,
    val contentId: String
)

// Media Sync Models
@Serializable

data class MediaUploadRequest(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray // Base64 encoded or raw bytes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as MediaUploadRequest
        
        if (contentId != other.contentId) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = contentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable

data class MediaUploadResponse(
    val contentId: String,
    val mediaId: String,
    val downloadUrl: String,
    val uploadedAt: Long
)

@Serializable

data class MediaDownloadResponse(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val downloadUrl: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as MediaDownloadResponse
        
        if (contentId != other.contentId) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (!data.contentEquals(other.data)) return false
        if (downloadUrl != other.downloadUrl) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = contentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + downloadUrl.hashCode()
        return result
    }
}