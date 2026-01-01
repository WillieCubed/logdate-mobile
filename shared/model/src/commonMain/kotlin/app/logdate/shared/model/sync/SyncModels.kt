package app.logdate.shared.model.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifier for the originating device of a sync operation.
 * Avoids nullable device IDs; defaults to [UNKNOWN] when a concrete device ID is not available.
 */
@JvmInline
@Serializable
value class DeviceId(val value: String) {
    companion object {
        val UNKNOWN = DeviceId("unknown")
    }
}

/**
 * Version constraint supplied by the client when updating existing entities.
 * Using a sealed hierarchy eliminates nullable version hints.
 */
@Serializable
sealed class VersionConstraint {
    /**
     * No version constraint provided; server may apply LWW or its own policy.
     */
    @Serializable
    @SerialName("none")
    data object None : VersionConstraint()

    /**
     * Client knows the last server version and wants conflict detection.
     */
    @Serializable
    @SerialName("known")
    data class Known(val serverVersion: Long) : VersionConstraint()
}

/**
 * All timestamps are milliseconds since epoch (UTC).
 */
@Serializable
data class ContentUploadRequest(
    val id: String,
    val type: String, // TEXT, IMAGE, VIDEO, AUDIO
    val content: String?,
    val mediaUri: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0,
    val deviceId: DeviceId = DeviceId.UNKNOWN
)

@Serializable
data class ContentUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable
data class ContentUpdateRequest(
    val content: String? = null,
    val mediaUri: String? = null,
    val lastUpdated: Long,
    val syncVersion: Long = 0,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    val versionConstraint: VersionConstraint = VersionConstraint.None
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
    val content: String? = null,
    val mediaUri: String? = null,
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

@Serializable
data class JournalUploadRequest(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0,
    val deviceId: DeviceId = DeviceId.UNKNOWN
)

@Serializable
data class JournalUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable
data class JournalUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val lastUpdated: Long,
    val syncVersion: Long = 0,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    val versionConstraint: VersionConstraint = VersionConstraint.None
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

@Serializable
data class AssociationUploadRequest(
    val associations: List<Association>
)

@Serializable
data class Association(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val syncVersion: Long = 0,
    val deviceId: DeviceId = DeviceId.UNKNOWN
) {
    fun key(): Pair<String, String> = journalId to contentId
}

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
) {
    fun key(): Pair<String, String> = journalId to contentId
}

@Serializable
data class MediaUploadRequest(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val deviceId: DeviceId = DeviceId.UNKNOWN
)

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
)
