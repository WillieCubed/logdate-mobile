package app.logdate.client.domain.export

import app.logdate.shared.model.Journal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.BufferedSink

/**
 * Result of a completed export. Holds domain objects and serializes each
 * category lazily so only one JSON string is in memory at a time.
 */
class ExportResult(
    private val json: Json,
    private val exportMetadata: ExportMetadata,
    private val journals: List<Journal>,
    private val exportNotes: List<ExportNote>,
    private val exportRelations: List<ExportJournalNoteRelation>,
    private val exportDrafts: List<ExportDraft>,
    private val profilePayload: ProfilePayload?,
    private val placesPayload: PlacesPayload?,
    private val locationHistoryPayload: LocationHistoryPayload?,
    val mediaFiles: List<ExportMediaFile>,
    val stats: ExportStats,
    internal val issues: List<ExportIssue>,
) {
    fun serializeMetadata(): String = json.encodeToString(exportMetadata)

    fun serializeJournals(): String = json.encodeToString(mapOf("journals" to journals))

    fun serializeNotes(): String = json.encodeToString(mapOf("notes" to exportNotes))

    fun serializeJournalNotes(): String = json.encodeToString(mapOf("journal_notes" to exportRelations))

    fun serializeDrafts(): String = json.encodeToString(mapOf("drafts" to exportDrafts))

    fun serializeProfile(): String? = profilePayload?.let { json.encodeToString(it) }

    fun serializePlaces(): String? = placesPayload?.let { json.encodeToString(it) }

    fun serializeLocationHistory(): String? = locationHistoryPayload?.let { json.encodeToString(it) }

    fun serializeMediaManifest(files: List<ExportMediaFile> = mediaFiles): String? =
        files.takeIf { it.isNotEmpty() }?.let { json.encodeToString(ExportMediaManifest(it)) }

    // Streaming encoders: write JSON directly to the archive sink instead of
    // materializing a full String (+ its byte-array copy). Peak memory for a
    // category becomes the okio buffer size rather than the whole category, which
    // is what keeps large exports (e.g. extensive location history) off the heap.

    @OptIn(ExperimentalSerializationApi::class)
    fun writeMetadata(sink: BufferedSink) = json.encodeToBufferedSink(exportMetadata, sink)

    @OptIn(ExperimentalSerializationApi::class)
    fun writeJournals(sink: BufferedSink) = json.encodeToBufferedSink(mapOf("journals" to journals), sink)

    @OptIn(ExperimentalSerializationApi::class)
    fun writeNotes(sink: BufferedSink) = json.encodeToBufferedSink(mapOf("notes" to exportNotes), sink)

    @OptIn(ExperimentalSerializationApi::class)
    fun writeJournalNotes(sink: BufferedSink) = json.encodeToBufferedSink(mapOf("journal_notes" to exportRelations), sink)

    @OptIn(ExperimentalSerializationApi::class)
    fun writeDrafts(sink: BufferedSink) = json.encodeToBufferedSink(mapOf("drafts" to exportDrafts), sink)

    val hasProfile: Boolean get() = profilePayload != null

    @OptIn(ExperimentalSerializationApi::class)
    fun writeProfile(sink: BufferedSink) {
        profilePayload?.let { json.encodeToBufferedSink(it, sink) }
    }

    val hasPlaces: Boolean get() = placesPayload != null

    @OptIn(ExperimentalSerializationApi::class)
    fun writePlaces(sink: BufferedSink) {
        placesPayload?.let { json.encodeToBufferedSink(it, sink) }
    }

    val hasLocationHistory: Boolean get() = locationHistoryPayload != null

    @OptIn(ExperimentalSerializationApi::class)
    fun writeLocationHistory(sink: BufferedSink) {
        locationHistoryPayload?.let { json.encodeToBufferedSink(it, sink) }
    }

    fun hasMediaManifest(files: List<ExportMediaFile> = mediaFiles): Boolean = files.isNotEmpty()

    @OptIn(ExperimentalSerializationApi::class)
    fun writeMediaManifest(
        sink: BufferedSink,
        files: List<ExportMediaFile> = mediaFiles,
    ) = json.encodeToBufferedSink(ExportMediaManifest(files), sink)

    fun renderIssuesText(additionalIssues: List<ExportIssue> = emptyList()): String? {
        val allIssues = (issues + additionalIssues).distinct()
        if (allIssues.isEmpty()) return null

        return buildString {
            appendLine("LogDate export issues")
            appendLine()
            appendLine("This export completed, but some recoverable problems were detected.")
            appendLine("Affected content may need to be re-exported after the source files or metadata are repaired.")
            appendLine()
            appendLine("Issues:")
            allIssues.forEach { issue ->
                appendLine("- ${issue.describe()}")
            }
            appendLine()
            appendLine("Possible mitigations:")
            appendLine("- Keep this archive; unaffected data was still exported.")
            appendLine("- Re-open the affected entries in LogDate and verify the media still loads.")
            appendLine("- If media is missing, reattach the original image, video, or recording and export again.")
            appendLine(
                "- If the app is referencing an old private file path, saving or re-importing the media can refresh the stored reference.",
            )
        }
    }
}

/**
 * Represents a media file to be included in the export.
 */
@Serializable
data class ExportMediaFile(
    val exportPath: String,
    val sourceUri: String,
)
