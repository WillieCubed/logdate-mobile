package app.logdate.client.domain.export

import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Data structure for the complete user data export that follows the LogDate export specification.
 */
@Serializable
data class LogDateExport(
    val metadata: ExportMetadata,
    val journals: List<Journal>,
    val notes: List<ExportNote>,
    val drafts: List<ExportDraft>,
    val profile: LogDateProfile? = null,
    val places: List<ExportPlace> = emptyList(),
    val locationHistory: List<ExportLocationHistoryItem> = emptyList(),
)

/**
 * Metadata about the export.
 */
@Serializable
data class ExportMetadata(
    val version: ExportSchemaVersion = ExportSchemaVersion.CURRENT,
    val exportDate: Instant,
    val userId: String,
    val deviceId: String,
    val appVersion: String,
    val stats: ExportStats,
)

/**
 * Export statistics.
 */
@Serializable
data class ExportStats(
    val journalCount: Int,
    val noteCount: Int,
    val draftCount: Int,
    val mediaCount: Int,
    val placeCount: Int = 0,
    val locationHistoryCount: Int = 0,
    val hasProfile: Boolean = false,
)

/**
 * Note data for export.
 *
 * Notes are exported independently of journals. Journal-note relationships
 * are stored in a separate schema file to support a note appearing in multiple journals.
 */
@Serializable
data class ExportNote(
    val id: String,
    val type: String, // "text", "image", "video", "audio"
    val content: String? = null, // Text content for text notes
    val caption: String? = null, // Caption for media notes
    val mediaPath: String? = null, // Path to media file
    val durationMs: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: ExportLocation? = null,
    val tags: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val syncVersion: Long = 0,
)

/**
 * Draft data for export.
 */
@Serializable
data class ExportDraft(
    val id: String,
    val journalId: String? = null, // Kept for backwards compatibility with older archives
    val journalIds: List<String> = emptyList(),
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: ExportLocation? = null,
    val mediaReferences: List<String> = emptyList(),
    val blocks: List<SerializableEntryBlock> = emptyList(),
)

/**
 * Location data for export.
 */
@Serializable
data class ExportLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val altitude: Double? = null,
    val accuracy: Float? = null,
)

/**
 * Journal-note relationship for export.
 *
 * Since notes can belong to multiple journals, we store these relationships separately.
 */
@Serializable
data class ExportJournalNoteRelation(
    val journalId: String,
    val noteId: String,
    val addedAt: Instant,
    val syncVersion: Long = 0,
)

@Serializable
data class ExportPlace(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 100.0,
    val description: String? = null,
)

@Serializable
data class ExportLocationHistoryItem(
    val sampleId: String,
    val userId: String,
    val deviceId: String,
    val timestamp: Instant,
    val loggedAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val confidence: Float,
    val isGenuine: Boolean,
    val capturePipeline: String,
    val captureSource: String,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val isMock: Boolean = false,
)

/**
 * Constants for the LogDate export file format.
 *
 * Both the export and import pipelines reference these constants
 * to keep MIME types and file extensions in sync.
 */
object ExportFormat {
    const val FILE_EXTENSION = "zip"
    const val MIME_TYPE = "application/zip"

    /**
     * MIME types the import picker should accept.
     *
     * Includes the canonical [MIME_TYPE] plus legacy/alternative types
     * to handle files from older app versions or renamed archives.
     */
    val ACCEPTED_IMPORT_MIME_TYPES =
        arrayOf(
            MIME_TYPE,
            "application/x-zip-compressed",
            "application/json",
            "application/octet-stream",
        )
}

/**
 * Export file structure definition.
 */
object ExportFileStructure {
    const val METADATA_FILE: String = "metadata.json"
    const val JOURNALS_FILE: String = "journals.json"
    const val NOTES_FILE: String = "notes.json"
    const val JOURNAL_NOTES_FILE: String = "journal_notes.json"
    const val DRAFTS_FILE: String = "drafts.json"
    const val EXPORT_ISSUES_FILE: String = "export_issues.txt"
    const val PROFILE_FILE: String = "profile.json"
    const val PLACES_FILE: String = "places.json"
    const val LOCATION_HISTORY_FILE: String = "location_history.json"
    const val MEDIA_MANIFEST_FILE: String = "media_manifest.json"
    const val MEDIA_FOLDER: String = "media"
}

@Serializable
data class ProfilePayload(
    val profile: LogDateProfile,
)

@Serializable
data class PlacesPayload(
    val places: List<ExportPlace>,
)

@Serializable
data class LocationHistoryPayload(
    @SerialName("location_history")
    val locationHistory: List<ExportLocationHistoryItem>,
)

@Serializable
data class ExportMediaManifest(
    val files: List<ExportMediaFile>,
)
