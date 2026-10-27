package app.logdate.client.data.restore

import app.logdate.client.data.journals.toModel
import app.logdate.client.data.notes.referencedPlaceId
import app.logdate.client.data.notes.toModel
import app.logdate.client.data.notes.toNotePlace
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.repository.transcription.TranscriptDocument
import kotlinx.serialization.decodeFromString
import kotlin.uuid.Uuid

internal data class RetainedSourceNotes(
    val values: List<Pair<String, JournalNote>>,
    val unresolvedRows: Int,
)

internal suspend fun RetainedJournalDatabaseMerger.readRetainedSourceNotes(): RetainedSourceNotes {
    var unresolvedRows = 0
    val values =
        buildList {
            val textNotes = sourceDatabase.textNoteDao().getAll()
            unresolvedRows += textNotes.count { it.deletedAt != null }
            for (entity in textNotes.filter { it.deletedAt == null }) {
                add("note_text" to entity.toModel(sourcePlace(entity)))
            }
            val imageNotes = sourceDatabase.imageNoteDao().getAll()
            unresolvedRows += imageNotes.count { it.deletedAt != null }
            for (entity in imageNotes.filter { it.deletedAt == null }) {
                add("note_image" to entity.toModel(sourcePlace(entity), sourceCaption(entity.uid)))
            }
            val videoNotes = sourceDatabase.videoNoteDao().getAll()
            unresolvedRows += videoNotes.count { it.deletedAt != null }
            for (entity in videoNotes.filter { it.deletedAt == null }) {
                add("note_video" to entity.toModel(sourcePlace(entity), sourceCaption(entity.uid)))
            }
            val audioNotes = sourceDatabase.audioNoteDao().getAll()
            unresolvedRows += audioNotes.count { it.deletedAt != null }
            for (entity in audioNotes.filter { it.deletedAt == null }) {
                var note = entity.toModel(sourcePlace(entity), sourceCaption(entity.uid))
                val transcript = sourceDatabase.transcriptionDao().getTranscriptionByNoteId(entity.uid)
                val documentJson = transcript?.documentJson
                if (documentJson != null) {
                    val document = runCatching { json.decodeFromString<TranscriptDocument>(documentJson) }.getOrNull()
                    if (document == null) unresolvedRows++ else note = note.copy(transcript = document)
                }
                add("note_audio" to note)
            }
        }
    return RetainedSourceNotes(values, unresolvedRows)
}

internal suspend fun RetainedJournalDatabaseMerger.sourcePlace(entity: app.logdate.client.database.entities.GenericNoteData) =
    entity.referencedPlaceId()?.let { id -> sourceDatabase.placeDao().getById(id)?.toNotePlace() }

internal suspend fun RetainedJournalDatabaseMerger.sourceCaption(id: Uuid): String =
    sourceDatabase
        .mediaCaptionDao()
        .getCaption(id)
        ?.caption
        .orEmpty()

internal suspend fun RetainedJournalDatabaseMerger.importNoteMedia(note: JournalNote): Pair<JournalNote, Boolean> {
    val original = note.mediaRefOrNull() ?: return note to false
    val asset = RetainedManagedMediaSource(sourceFiles, additionalMediaSources).resolve(original)
    if (asset == null) {
        requireCurrentScope()
        val recovered = verifiedCanonicalMediaReference(original) { media.isRecoveredOriginal(it, StoredMediaReferences.Unchanged) }
        requireCurrentScope()
        return if (recovered != null) {
            note.withRetainedMediaRef(recovered) to false
        } else {
            note.withRetainedMediaRef(retainedUnavailableMediaReference(original)) to true
        }
    }
    requireCurrentScope()
    // Android's canonical media store is content-addressed. Reimporting the verified retained
    // bytes is deterministic and deduplicates safely; a same-looking working reference alone
    // is not proof that it contains this snapshot's media.
    val uri = media.saveMediaFromFile(asset.file.absolutePath, asset.fileName, asset.mimeType)
    requireCurrentScope()
    return note.withRetainedMediaRef(uri) to false
}

internal fun JournalNote.withRetainedMergeId(id: Uuid): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(uid = id)
        is JournalNote.Handwriting -> copy(uid = id)
        is JournalNote.Image -> copy(uid = id)
        is JournalNote.Video -> copy(uid = id)
        is JournalNote.Audio -> copy(uid = id)
    }

internal fun JournalNote.withRetainedMediaRef(ref: String): JournalNote =
    when (this) {
        is JournalNote.Text, is JournalNote.Handwriting -> this
        is JournalNote.Image -> copy(mediaRef = ref)
        is JournalNote.Video -> copy(mediaRef = ref)
        is JournalNote.Audio -> copy(mediaRef = ref)
    }
