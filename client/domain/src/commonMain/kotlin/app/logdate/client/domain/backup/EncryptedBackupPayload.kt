package app.logdate.client.domain.backup

import app.logdate.client.domain.restore.RestoreBundle
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedBackupPayload(
    val metadataJson: String,
    val journalsJson: String,
    val notesJson: String,
    val journalNotesJson: String,
    val draftsJson: String,
    val profileJson: String? = null,
    val placesJson: String? = null,
    val locationHistoryJson: String? = null,
    val mediaManifestJson: String? = null,
    val mediaFiles: List<EncryptedBackupMediaFile> = emptyList(),
) {
    fun toRestoreBundle(): RestoreBundle =
        RestoreBundle(
            metadataJson = metadataJson,
            journalsJson = journalsJson,
            notesJson = notesJson,
            journalNotesJson = journalNotesJson,
            draftsJson = draftsJson,
            profileJson = profileJson,
            placesJson = placesJson,
            locationHistoryJson = locationHistoryJson,
            mediaManifestJson = mediaManifestJson,
        )
}

@Serializable
data class EncryptedBackupMediaFile(
    val exportPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String,
)
