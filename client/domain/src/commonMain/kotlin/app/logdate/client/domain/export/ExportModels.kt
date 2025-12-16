package app.logdate.client.domain.export

import app.logdate.shared.model.Journal
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data structure for the complete user data export that follows the LogDate export specification.
 */
@Serializable
data class LogDateExport(
    val metadata: ExportMetadata,
    val journals: List<Journal>,
    val notes: List<ExportNote>,
    val drafts: List<ExportDraft>
)

/**
 * Metadata about the export.
 */
@Serializable
data class ExportMetadata(
    val version: String = "1.0",
    val exportDate: Instant,
    val userId: String,
    val deviceId: String,
    val appVersion: String,
    val stats: ExportStats
)

/**
 * Export statistics.
 */
@Serializable
data class ExportStats(
    val journalCount: Int,
    val noteCount: Int,
    val draftCount: Int,
    val mediaCount: Int
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
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: ExportLocation? = null,
    val tags: List<String> = emptyList(),
    val people: List<String> = emptyList()
)

/**
 * Draft data for export.
 */
@Serializable
data class ExportDraft(
    val id: String,
    val journalId: String? = null, // Optional journal ID
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: ExportLocation? = null,
    val mediaReferences: List<String> = emptyList()
)

/**
 * Location data for export.
 */
@Serializable
data class ExportLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null
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
    val addedAt: Instant
)

/**
 * Export file structure definition.
 */
data class ExportFileStructure(
    val metadataFile: String = "metadata.json",
    val journalsFile: String = "journals.json",
    val notesFile: String = "notes.json",
    val journalNotesFile: String = "journal_notes.json",
    val draftsFile: String = "drafts.json",
    val mediaFolder: String = "media"
)