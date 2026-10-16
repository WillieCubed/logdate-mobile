package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.MediaCaptionDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.entities.MediaCaptionEntity
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class JournalNoteRecoveryPersistence(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val audioNoteDao: AudioNoteDao,
    private val videoNoteDao: VideoNoteDao,
    private val mediaCaptionDao: MediaCaptionDao,
    private val transcriptionRepository: TranscriptionRepository?,
    private val transactionManager: SyncTransactionManager,
    private val syncMetadataService: SyncMetadataService,
    private val mediaReferences: StoredMediaReferences,
    private val getNoteById: suspend (Uuid) -> JournalNote?,
    private val currentScope: suspend () -> JournalMergeScope?,
    private val changed: () -> Unit,
    private val indexAvailableMedia: suspend (JournalNote) -> Unit,
    private val receipts: app.logdate.client.data.journals.ArchiveRecoveryReceipts,
) {
    private fun JournalNote.withStoredMediaRef(): JournalNote =
        when (this) {
            is JournalNote.Image -> copy(mediaRef = mediaReferences.storedReference(mediaRef))
            is JournalNote.Audio -> copy(mediaRef = mediaReferences.storedReference(mediaRef))
            is JournalNote.Video -> copy(mediaRef = mediaReferences.storedReference(mediaRef))
            else -> this
        }

    suspend fun compareAndSet(
        expected: JournalNote?,
        replacement: JournalNote,
        scope: JournalMergeScope?,
        recoveryId: String?,
    ): Boolean {
        require(expected == null || expected.uid == replacement.uid)
        val saved =
            transactionManager.withTransaction {
                check(currentScope() == scope) { "Note recovery account changed" }
                if (scope != null &&
                    recoveryId != null &&
                    receipts.contains(scope, "NOTE", recoveryId, replacement.uid.toString())
                ) {
                    return@withTransaction false
                }
                val current = getNoteById(replacement.uid)
                if (current != expected || current != null && current.type != replacement.type || expected == replacement) {
                    recordDecision(scope, recoveryId, replacement.uid)
                    return@withTransaction current == expected && expected == replacement
                }
                val operation = if (expected == null) PendingOperation.CREATE else PendingOperation.UPDATE
                val uploadScope = scope?.let { UploadScope(it.ownerId, it.serverOrigin) }
                if (!syncMetadataService.enqueueRestoreMutation(
                        replacement.uid.toString(),
                        EntityType.NOTE,
                        operation,
                        uploadScope,
                    )
                ) {
                    recordDecision(scope, recoveryId, replacement.uid)
                    return@withTransaction false
                }
                val note = replacement.withStoredMediaRef()
                if (expected == null) {
                    createFromSync(note)
                } else {
                    val count =
                        when (note) {
                            is JournalNote.Text -> textNoteDao.updateForRestore(note.toEntity())
                            is JournalNote.Image -> imageNoteDao.updateForRestore(note.toEntity())
                            is JournalNote.Audio -> audioNoteDao.updateForRestore(note.toEntity())
                            is JournalNote.Video -> videoNoteDao.updateForRestore(note.toEntity())
                        }
                    check(count == 1) { "Note changed during archive recovery" }
                    when (note) {
                        is JournalNote.Text -> Unit
                        is JournalNote.Image -> mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                        is JournalNote.Video -> mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                        is JournalNote.Audio -> {
                            val previous = (expected as JournalNote.Audio).mediaRef
                            if (previous != note.mediaRef) transcriptionRepository?.rebindMediaReference(note.uid, previous, note.mediaRef)
                            mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                            note.persistTranscript(transcriptionRepository)
                        }
                    }
                }
                indexAvailableMedia(note)
                recordDecision(scope, recoveryId, note.uid)
                true
            }
        if (saved) changed()
        return saved
    }

    private suspend fun recordDecision(
        scope: JournalMergeScope?,
        recoveryId: String?,
        id: Uuid,
    ) {
        if (scope != null && recoveryId != null) receipts.record(scope, "NOTE", recoveryId, id.toString())
        check(currentScope() == scope) { "Note recovery account changed" }
    }

    suspend fun createFromSync(note: JournalNote) {
        when (val storedNote = note.withStoredMediaRef()) {
            is JournalNote.Text -> textNoteDao.addNote(storedNote.toEntity())
            is JournalNote.Image -> {
                imageNoteDao.addNote(storedNote.toEntity())
                mediaCaptionDao.upsertCaption(MediaCaptionEntity(storedNote.uid, storedNote.caption))
            }
            is JournalNote.Audio -> {
                audioNoteDao.addNote(storedNote.toEntity())
                mediaCaptionDao.upsertCaption(MediaCaptionEntity(storedNote.uid, storedNote.caption))
                storedNote.persistTranscript(transcriptionRepository)
            }
            is JournalNote.Video -> {
                videoNoteDao.addNote(storedNote.toEntity())
                mediaCaptionDao.upsertCaption(MediaCaptionEntity(storedNote.uid, storedNote.caption))
            }
        }
    }

    suspend fun deleteFromSync(noteId: Uuid) {
        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        transcriptionRepository?.deleteTranscription(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)
        mediaCaptionDao.deleteCaption(noteId)
    }

    suspend fun updateSyncMetadata(
        note: JournalNote,
        syncVersion: Long,
        syncedAt: Instant,
    ) {
        when (note) {
            is JournalNote.Text -> textNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Image -> imageNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Audio -> audioNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Video -> videoNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
        }
    }

    suspend fun updateMediaRef(
        noteId: Uuid,
        mediaRef: String,
    ) {
        val storedRef = mediaReferences.storedReference(mediaRef)
        transactionManager.withTransaction {
            val previous = (getNoteById(noteId) as? JournalNote.Audio)?.mediaRef
            if (previous != null) transcriptionRepository?.rebindMediaReference(noteId, previous, storedRef)
            imageNoteDao.updateContentUri(noteId, storedRef)
            audioNoteDao.updateContentUri(noteId, storedRef)
            videoNoteDao.updateContentUri(noteId, storedRef)
        }
    }
}
