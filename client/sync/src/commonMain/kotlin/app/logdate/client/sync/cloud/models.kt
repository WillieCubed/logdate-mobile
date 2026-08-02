@file:Suppress("ktlint:standard:filename")

package app.logdate.client.sync.cloud

// Re-export shared sync models to avoid duplicate domain definitions.
typealias ContentUploadRequest = app.logdate.shared.model.sync.ContentUploadRequest
typealias ContentUploadResponse = app.logdate.shared.model.sync.ContentUploadResponse
typealias ContentUpdateRequest = app.logdate.shared.model.sync.ContentUpdateRequest
typealias ContentUpdateResponse = app.logdate.shared.model.sync.ContentUpdateResponse
typealias ContentChangesResponse = app.logdate.shared.model.sync.ContentChangesResponse
typealias ContentChange = app.logdate.shared.model.sync.ContentChange
typealias ContentDeletion = app.logdate.shared.model.sync.ContentDeletion

typealias JournalUploadRequest = app.logdate.shared.model.sync.JournalUploadRequest
typealias JournalUploadResponse = app.logdate.shared.model.sync.JournalUploadResponse
typealias JournalUpdateRequest = app.logdate.shared.model.sync.JournalUpdateRequest
typealias JournalUpdateResponse = app.logdate.shared.model.sync.JournalUpdateResponse
typealias JournalChangesResponse = app.logdate.shared.model.sync.JournalChangesResponse
typealias JournalChange = app.logdate.shared.model.sync.JournalChange
typealias JournalDeletion = app.logdate.shared.model.sync.JournalDeletion

typealias AssociationUploadRequest = app.logdate.shared.model.sync.AssociationUploadRequest
typealias Association = app.logdate.shared.model.sync.Association
typealias AssociationUploadResponse = app.logdate.shared.model.sync.AssociationUploadResponse
typealias AssociationChangesResponse = app.logdate.shared.model.sync.AssociationChangesResponse
typealias AssociationChange = app.logdate.shared.model.sync.AssociationChange
typealias AssociationDeletion = app.logdate.shared.model.sync.AssociationDeletion
typealias AssociationDeleteRequest = app.logdate.shared.model.sync.AssociationDeleteRequest
typealias AssociationDeleteItem = app.logdate.shared.model.sync.AssociationDeleteItem

typealias DeviceId = app.logdate.shared.model.sync.DeviceId
typealias MediaUploadRequest = app.logdate.shared.model.sync.MediaUploadRequest
typealias MediaUploadResponse = app.logdate.shared.model.sync.MediaUploadResponse
typealias MediaMetadataResponse = app.logdate.shared.model.sync.MediaMetadataResponse
typealias MediaDownloadResponse = app.logdate.shared.model.sync.MediaDownloadResponse

typealias BackupUploadRequest = app.logdate.shared.model.sync.BackupUploadRequest
typealias BackupUploadResponse = app.logdate.shared.model.sync.BackupUploadResponse
typealias BackupInfoResponse = app.logdate.shared.model.sync.BackupInfoResponse
typealias BackupListResponse = app.logdate.shared.model.sync.BackupListResponse

/** Metadata and plaintext backup bytes returned by the authenticated download flow. */
data class BackupDownloadResponse(
    val metadata: BackupInfoResponse,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupDownloadResponse) return false
        return metadata == other.metadata && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * metadata.hashCode() + data.contentHashCode()
}

typealias DraftUploadRequest = app.logdate.shared.model.sync.DraftUploadRequest
typealias DraftUploadResponse = app.logdate.shared.model.sync.DraftUploadResponse
typealias DraftChangesResponse = app.logdate.shared.model.sync.DraftChangesResponse
typealias DraftChange = app.logdate.shared.model.sync.DraftChange
