package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RestoreUserDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun restore(
        bundle: RestoreBundle,
        options: RestoreOptions = RestoreOptions(),
        mediaImporter: MediaImporter? = null,
    ): RestoreResult {
        val metadata = json.decodeFromString<ExportMetadata>(bundle.metadataJson)
        val journalsPayload = json.decodeFromString<JournalsPayload>(bundle.journalsJson)
        val notesPayload = json.decodeFromString<NotesPayload>(bundle.notesJson)
        val draftsPayload = json.decodeFromString<DraftsPayload>(bundle.draftsJson)
        val journalNotesPayload = json.decodeFromString<JournalNotesPayload>(bundle.journalNotesJson)
        val manifest = bundle.mediaManifestJson?.let { json.decodeFromString<ExportMediaManifest>(it) }
        val manifestIndex = manifest?.files?.associateBy { it.sourceUri }.orEmpty()

        val syncableJournals = journalRepository as? SyncableJournalRepository
        val syncableNotes = journalNotesRepository as? SyncableJournalNotesRepository
        val syncableContent = journalContentRepository as? SyncableJournalContentRepository

        var journalsImported = 0
        var notesImported = 0
        var draftsImported = 0
        var linksImported = 0
        var mediaImported = 0
        val warnings = mutableListOf<String>()

        for (journal in journalsPayload.journals) {
            val existing = journalRepository.getJournalById(journal.id)
            val shouldWrite = shouldOverwrite(existing?.lastUpdated, journal.lastUpdated, options.strategy)
            if (existing == null) {
                if (syncableJournals != null) {
                    syncableJournals.createFromSync(journal)
                } else {
                    journalRepository.create(journal)
                }
                journalsImported++
            } else if (shouldWrite) {
                if (syncableJournals != null) {
                    syncableJournals.updateFromSync(journal)
                } else {
                    journalRepository.update(journal)
                }
                journalsImported++
            }
        }

        for (note in notesPayload.notes) {
            val parsedId = parseUuid(note.id, warnings) ?: continue
            val mediaResolution = resolveMediaReference(note.mediaPath, manifestIndex, mediaImporter)
            if (mediaResolution.imported) {
                mediaImported++
            }
            val restored = note.toJournalNote(parsedId, mediaResolution.uri)
            if (restored == null) {
                val normalizedType = note.type.lowercase()
                val message =
                    if (normalizedType == "image" || normalizedType == "video" || normalizedType == "audio") {
                        "Missing media reference for note ${note.id}"
                    } else {
                        "Unsupported note type: ${note.type}"
                    }
                warnings.add(message)
                continue
            }

            val existing = journalNotesRepository.getNoteById(parsedId)
            val shouldWrite = shouldOverwrite(existing?.lastUpdated, restored.lastUpdated, options.strategy)
            if (existing == null) {
                if (syncableNotes != null) {
                    syncableNotes.createFromSync(restored)
                } else {
                    journalNotesRepository.create(restored)
                }
                notesImported++
            } else if (shouldWrite) {
                if (syncableNotes != null) {
                    syncableNotes.deleteFromSync(parsedId)
                    syncableNotes.createFromSync(restored)
                } else {
                    journalNotesRepository.removeById(parsedId)
                    journalNotesRepository.create(restored)
                }
                notesImported++
            }
        }

        for (relation in journalNotesPayload.journalNotes) {
            val journalId = parseUuid(relation.journalId, warnings) ?: continue
            val noteId = parseUuid(relation.noteId, warnings) ?: continue

            if (syncableContent != null) {
                syncableContent.addContentToJournalFromSync(noteId, journalId)
            } else {
                journalContentRepository.addContentToJournal(noteId, journalId)
            }
            linksImported++
        }

        if (options.includeDrafts) {
            for (draft in draftsPayload.drafts) {
                val restored =
                    restoreDraft(draft, manifestIndex, mediaImporter) { imported ->
                        if (imported) {
                            mediaImported++
                        }
                    }
                journalRepository.saveDraft(restored)
                draftsImported++
            }
        }

        return RestoreResult(
            metadata = metadata,
            journalsImported = journalsImported,
            notesImported = notesImported,
            draftsImported = draftsImported,
            journalLinksImported = linksImported,
            mediaImported = mediaImported,
            warnings = warnings,
        )
    }

    private fun shouldOverwrite(
        existing: Instant?,
        incoming: Instant,
        strategy: RestoreStrategy,
    ): Boolean =
        when (strategy) {
            RestoreStrategy.MERGE_KEEP_NEWEST -> existing == null || incoming > existing
            RestoreStrategy.REPLACE_EXISTING -> true
        }

    private fun parseUuid(
        value: String,
        warnings: MutableList<String>,
    ): Uuid? =
        runCatching { Uuid.parse(value) }
            .onFailure { warnings.add("Invalid UUID in restore payload: $value") }
            .getOrNull()

    private suspend fun resolveMediaReference(
        sourceUri: String?,
        manifestIndex: Map<String, ExportMediaFile>,
        mediaImporter: MediaImporter?,
    ): MediaResolution {
        if (sourceUri.isNullOrBlank()) {
            return MediaResolution(null, false)
        }
        val manifestEntry = manifestIndex[sourceUri]
        val exportPath = manifestEntry?.exportPath ?: sourceUri
        val imported = mediaImporter?.importMedia(exportPath)
        return if (imported != null) {
            MediaResolution(imported, true)
        } else {
            MediaResolution(sourceUri, false)
        }
    }

    private suspend fun restoreDraft(
        draft: ExportDraft,
        manifestIndex: Map<String, ExportMediaFile>,
        mediaImporter: MediaImporter?,
        onMediaImported: (Boolean) -> Unit,
    ): EditorDraft {
        val blocks = mutableListOf<SerializableEntryBlock>()
        if (draft.content.isNotBlank()) {
            blocks.add(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = draft.createdAt,
                    locationLat = draft.location?.latitude,
                    locationLng = draft.location?.longitude,
                    content = draft.content,
                ),
            )
        }

        draft.mediaReferences.forEach { reference ->
            val resolution = resolveMediaReference(reference, manifestIndex, mediaImporter)
            onMediaImported(resolution.imported)
            val uri = resolution.uri ?: return@forEach
            blocks.add(
                SerializableCameraBlock(
                    id = Uuid.random(),
                    timestamp = draft.updatedAt,
                    uri = uri,
                ),
            )
        }

        val selectedJournalIds =
            draft.journalId?.let { journalId ->
                runCatching { listOf(Uuid.parse(journalId)) }.getOrNull() ?: emptyList()
            } ?: emptyList()

        return EditorDraft(
            id = runCatching { Uuid.parse(draft.id) }.getOrDefault(Uuid.random()),
            blocks = blocks,
            selectedJournalIds = selectedJournalIds,
            createdAt = draft.createdAt,
            lastModifiedAt = draft.updatedAt,
        )
    }

    private fun ExportNote.toJournalNote(
        id: Uuid,
        mediaUri: String?,
    ): JournalNote? {
        val location = location?.toNoteLocation()
        return when (type.lowercase()) {
            "text" ->
                JournalNote.Text(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    content = content.orEmpty(),
                    location = location,
                )
            "image" ->
                JournalNote.Image(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = (mediaUri ?: mediaPath)?.takeIf { it.isNotBlank() } ?: return null,
                    location = location,
                )
            "video" ->
                JournalNote.Video(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = (mediaUri ?: mediaPath)?.takeIf { it.isNotBlank() } ?: return null,
                    location = location,
                )
            "audio" ->
                JournalNote.Audio(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = (mediaUri ?: mediaPath)?.takeIf { it.isNotBlank() } ?: return null,
                    durationMs = 0,
                    location = location,
                )
            else -> null
        }
    }

    private fun app.logdate.client.domain.export.ExportLocation.toNoteLocation(): NoteLocation =
        NoteLocation(
            coordinates = NoteCoordinates(latitude, longitude),
        )
}

interface MediaImporter {
    suspend fun importMedia(exportPath: String): String?
}

data class RestoreBundle(
    val metadataJson: String,
    val journalsJson: String,
    val notesJson: String,
    val journalNotesJson: String,
    val draftsJson: String,
    val mediaManifestJson: String? = null,
)

data class RestoreOptions(
    val strategy: RestoreStrategy = RestoreStrategy.MERGE_KEEP_NEWEST,
    val includeDrafts: Boolean = true,
)

enum class RestoreStrategy {
    MERGE_KEEP_NEWEST,
    REPLACE_EXISTING,
}

data class RestoreResult(
    val metadata: ExportMetadata,
    val journalsImported: Int,
    val notesImported: Int,
    val draftsImported: Int,
    val journalLinksImported: Int,
    val mediaImported: Int,
    val warnings: List<String>,
)

private data class MediaResolution(
    val uri: String?,
    val imported: Boolean,
)

@Serializable
private data class JournalsPayload(
    val journals: List<Journal>,
)

@Serializable
private data class NotesPayload(
    val notes: List<ExportNote>,
)

@Serializable
private data class DraftsPayload(
    val drafts: List<ExportDraft>,
)

@Serializable
private data class JournalNotesPayload(
    @SerialName("journal_notes")
    val journalNotes: List<ExportJournalNoteRelation>,
)
