package app.logdate.client.domain.export

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Use case for exporting all user data to JSON format according to the LogDate export specification.
 */
class ExportUserDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val userStateRepository: UserStateRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val appInfoProvider: AppInfoProvider,
    private val getAllAudioNotesUseCase: GetAllAudioNotesUseCase,
) {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    /**
     * Exports user data as a Flow that emits progress updates.
     *
     * @param includeJournals Whether to include journals in the export
     * @param includeNotes Whether to include notes in the export
     * @param includeDrafts Whether to include drafts in the export
     * @param includeMedia Whether to include media files in the export
     * @param dateRangeCutoff Optional cutoff instant — notes and drafts created before this are excluded.
     *                        Journals are always fully included when [includeJournals] is true.
     * @return Flow of ExportProgress containing current status and completion percentage
     */
    fun exportUserData(
        includeJournals: Boolean = true,
        includeNotes: Boolean = true,
        includeDrafts: Boolean = true,
        includeMedia: Boolean = true,
        dateRangeCutoff: kotlin.time.Instant? = null,
    ): Flow<ExportProgress> =
        flow {
            emit(ExportProgress.Starting)

            try {
                // Gather journals (always fully exported if included — they're containers, not time-bound)
                emit(ExportProgress.InProgress(0.1f, "Collecting journals..."))
                val journals =
                    if (includeJournals) {
                        journalRepository.allJournalsObserved.first()
                    } else {
                        emptyList()
                    }

                // Gather notes, filtered by date range if specified
                emit(ExportProgress.InProgress(0.3f, "Collecting notes..."))
                val notes =
                    if (includeNotes) {
                        val allNotes = journalNotesRepository.allNotesObserved.first()
                        if (dateRangeCutoff != null) {
                            allNotes.filter { it.creationTimestamp >= dateRangeCutoff }
                        } else {
                            allNotes
                        }
                    } else {
                        emptyList()
                    }

                // Gather audio notes for media export
                val audioNotes =
                    if (includeMedia && includeNotes) {
                        val allAudio = getAllAudioNotesUseCase().first()
                        if (dateRangeCutoff != null) {
                            allAudio.filter { it.creationTimestamp >= dateRangeCutoff }
                        } else {
                            allAudio
                        }
                    } else {
                        emptyList()
                    }

                // Gather drafts, filtered by date range if specified
                emit(ExportProgress.InProgress(0.5f, "Collecting drafts..."))
                val drafts =
                    if (includeDrafts) {
                        val allDrafts = journalRepository.getAllDrafts()
                        if (dateRangeCutoff != null) {
                            allDrafts.filter { it.createdAt >= dateRangeCutoff }
                        } else {
                            allDrafts
                        }
                    } else {
                        emptyList()
                    }

                // Get app info and user ID
                val appInfo = appInfoProvider.getAppInfo()
                val userId = deviceIdProvider.getDeviceId().value.toString()
                val deviceId = deviceIdProvider.getDeviceId().value.toString()

                // Create export data structure
                emit(ExportProgress.InProgress(0.7f, "Preparing export data..."))

                // Map repository data to export models
                val exportNotes =
                    notes.map { note ->
                        when (note) {
                            is JournalNote.Text ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "text",
                                    content = note.content,
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                )
                            is JournalNote.Image ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "image",
                                    mediaPath = note.mediaRef,
                                    caption = note.caption.takeIf { it.isNotEmpty() },
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                )
                            is JournalNote.Audio ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "audio",
                                    mediaPath = note.mediaRef,
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                )
                            is JournalNote.Video ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "video",
                                    mediaPath = note.mediaRef,
                                    caption = note.caption.takeIf { it.isNotEmpty() },
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                )
                        }
                    }

                val exportDrafts = drafts.map { it.toExportDraft() }
                val exportRelations =
                    if (includeJournals && includeNotes) {
                        journals.flatMap { journal ->
                            journalNotesRepository
                                .observeNotesInJournal(journal.id)
                                .first()
                                .filter { note -> dateRangeCutoff == null || note.creationTimestamp >= dateRangeCutoff }
                                .map { note ->
                                    ExportJournalNoteRelation(
                                        journalId = journal.id.toString(),
                                        noteId = note.uid.toString(),
                                        addedAt = note.creationTimestamp,
                                    )
                                }
                        }
                    } else {
                        emptyList()
                    }

                // Calculate actual stats
                val mediaFiles =
                    if (includeMedia) {
                        getMediaFilesToExport(exportNotes, exportDrafts, audioNotes)
                    } else {
                        emptyList()
                    }

                val stats =
                    ExportStats(
                        journalCount = journals.size,
                        noteCount = notes.size,
                        draftCount = exportDrafts.size,
                        mediaCount = mediaFiles.size,
                    )

                val exportMetadata =
                    ExportMetadata(
                        exportDate = Clock.System.now(),
                        userId = userId,
                        deviceId = deviceId,
                        appVersion = appInfo.versionName,
                        stats = stats,
                    )

                // Create the separate export files
                val metadataJson = json.encodeToString(exportMetadata)
                val journalsJson = json.encodeToString(mapOf("journals" to journals))
                val notesJson = json.encodeToString(mapOf("notes" to exportNotes))
                val draftsJson = json.encodeToString(mapOf("drafts" to exportDrafts))
                val journalNotesJson = json.encodeToString(mapOf("journal_notes" to exportRelations))

                val mediaManifestJson = json.encodeToString(ExportMediaManifest(mediaFiles))

                // Create the final export data
                val exportData =
                    ExportResult(
                        metadata = metadataJson,
                        journals = journalsJson,
                        notes = notesJson,
                        journalNotes = journalNotesJson,
                        drafts = draftsJson,
                        mediaFiles = mediaFiles,
                        mediaManifest = mediaManifestJson,
                        stats = stats,
                    )

                emit(ExportProgress.Completed(exportData))
            } catch (exception: Exception) {
                emit(ExportProgress.Failed(exception.message ?: "Unknown error occurred"))
            }
        }

    /**
     * Resolves an [ExportDateRange] to a cutoff [Instant], or null for [ExportDateRange.AllTime].
     */
    companion object {
        fun resolveDateRangeCutoff(dateRange: String): kotlin.time.Instant? {
            val now = Clock.System.now()
            return when (dateRange) {
                "all_time" -> null
                "last_30_days" -> now - 30.days
                "last_90_days" -> now - 90.days
                "last_year" -> now - 365.days
                else -> null
            }
        }
    }

    /**
     * Creates a standardized media path for export according to the specification.
     */
    private fun createMediaPath(
        uri: String,
        timestamp: kotlin.time.Instant,
    ): String {
        val year = timestamp.toString().substring(0, 4)
        val formattedTimestamp = timestamp.toString().replace(":", "-")
        val id = uri.substringAfterLast("/")
        val extension = uri.substringAfterLast(".", "")

        return if (extension.isNotEmpty()) {
            "$year/${formattedTimestamp}_$id.$extension"
        } else {
            "$year/${formattedTimestamp}_$id"
        }
    }

    private fun getMediaFilesToExport(
        notes: List<ExportNote>,
        drafts: List<ExportDraft>,
        audioNotes: List<JournalNote.Audio> = emptyList(),
    ): List<ExportMediaFile> {
        val mediaFilesByPath = linkedMapOf<String, String>()

        // Process regular notes with media
        notes.forEach { note ->
            if (note.mediaPath != null) {
                val exportPath = createMediaPath(note.mediaPath, note.createdAt)
                mediaFilesByPath[exportPath] = note.mediaPath
            }
        }

        // Process draft media references
        drafts.forEach { draft ->
            draft.mediaReferences.forEach { mediaRef ->
                val exportPath = createMediaPath(mediaRef, draft.createdAt)
                mediaFilesByPath[exportPath] = mediaRef
            }
        }

        // Process audio notes
        audioNotes.forEach { audioNote ->
            val exportPath = createMediaPath(audioNote.mediaRef, audioNote.creationTimestamp)
            mediaFilesByPath[exportPath] = audioNote.mediaRef
        }

        return mediaFilesByPath.map { (exportPath, sourceUri) ->
            ExportMediaFile(exportPath, sourceUri)
        }
    }

    private fun NoteLocation.toExportLocation(): ExportLocation? {
        val lat = effectiveLatitude ?: return null
        val lng = effectiveLongitude ?: return null
        return ExportLocation(lat, lng, displayName)
    }

    private fun EditorDraft.toExportDraft(): ExportDraft {
        val location =
            blocks.firstNotNullOfOrNull { block ->
                val latitude = block.locationLat
                val longitude = block.locationLng
                if (latitude != null && longitude != null) {
                    ExportLocation(
                        latitude = latitude,
                        longitude = longitude,
                    )
                } else {
                    null
                }
            }

        val content =
            blocks
                .filterIsInstance<SerializableTextBlock>()
                .joinToString("\n") { it.content }

        val mediaReferences =
            buildList {
                blocks
                    .filterIsInstance<SerializableImageBlock>()
                    .mapNotNullTo(this) { it.uri }
                blocks.filterIsInstance<SerializableVideoBlock>().forEach { block ->
                    listOfNotNull(block.uri, block.thumbnailUri).forEach { uri ->
                        add(uri)
                    }
                }
                blocks
                    .filterIsInstance<SerializableAudioBlock>()
                    .mapNotNullTo(this) { it.uri }
                blocks
                    .filterIsInstance<SerializableCameraBlock>()
                    .mapNotNullTo(this) { it.uri }
            }.distinct()

        val journalId = selectedJournalIds.firstOrNull()?.toString()

        return ExportDraft(
            id = id.toString(),
            journalId = journalId,
            content = content,
            createdAt = createdAt,
            updatedAt = lastModifiedAt,
            location = location,
            mediaReferences = mediaReferences,
        )
    }
}

/**
 * Represents the progress of the export operation.
 */
sealed class ExportProgress {
    /**
     * Export operation is starting.
     */
    data object Starting : ExportProgress()

    /**
     * Export operation is in progress.
     */
    data class InProgress(
        val percentage: Float,
        val message: String,
    ) : ExportProgress()

    /**
     * Export operation completed successfully.
     */
    data class Completed(
        val result: ExportResult,
    ) : ExportProgress()

    /**
     * Export operation failed.
     */
    data class Failed(
        val reason: String,
    ) : ExportProgress()
}

/**
 * Result of a successful export operation.
 */
data class ExportResult(
    val metadata: String,
    val journals: String,
    val notes: String,
    val journalNotes: String,
    val drafts: String,
    val mediaFiles: List<ExportMediaFile>,
    val mediaManifest: String? = null,
    val stats: ExportStats,
)

/**
 * Represents a media file to be included in the export.
 */
@Serializable
data class ExportMediaFile(
    val exportPath: String,
    val sourceUri: String,
)
