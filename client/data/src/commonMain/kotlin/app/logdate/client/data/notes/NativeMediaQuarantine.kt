package app.logdate.client.data.notes

import app.logdate.client.data.journals.NativeDraftMediaQuarantine
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableVideoBlock

/** Removes destination-ambiguous provider identities before restored content becomes visible. */
class NativeMediaQuarantine(
    private val database: LogDateDatabase,
    private val drafts: NativeDraftMediaQuarantine,
    private val transactions: SyncTransactionManager,
    private val transcriptions: TranscriptionRepository,
) {
    suspend fun run(isCurrent: () -> Boolean) {
        check(isCurrent()) { "Native media delivery changed" }
        transactions.withTransaction {
            database.imageNoteDao().getAll().forEach { note ->
                quarantined(note.contentUri)?.let { database.imageNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it) }
            }
            database.audioNoteDao().getAll().forEach { note ->
                quarantined(note.contentUri)?.let {
                    database.audioNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it)
                    transcriptions.rebindMediaReference(note.uid, note.contentUri, it)
                }
            }
            database.videoNoteDao().getAll().forEach { note ->
                quarantined(note.contentUri)?.let { database.videoNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it) }
            }
            database.journalDao().getAll().forEach { journal ->
                journal.coverImageUri?.let { previous ->
                    quarantined(previous)?.let { database.journalDao().updateCoverImageUriIfUnchanged(journal.id, previous, it) }
                }
            }
            check(isCurrent()) { "Native media delivery changed" }
        }
        drafts.run(isCurrent)
        check(isCurrent()) { "Native media delivery changed" }
    }

    private fun quarantined(reference: String): String? =
        reference.takeIf(UnavailableMediaReferences::requiresNativeQuarantine)?.let(UnavailableMediaReferences::nativeRestore)
}

internal fun quarantineNativeDraft(draft: EditorDraft): EditorDraft =
    draft.copy(
        blocks =
            draft.blocks.map { block ->
                fun repair(reference: String?): String? =
                    reference?.let {
                        if (UnavailableMediaReferences.requiresNativeQuarantine(it)) UnavailableMediaReferences.nativeRestore(it) else it
                    }
                when (block) {
                    is SerializableImageBlock -> block.copy(uri = repair(block.uri))
                    is SerializableVideoBlock -> block.copy(uri = repair(block.uri), thumbnailUri = repair(block.thumbnailUri))
                    is SerializableAudioBlock -> block.copy(uri = repair(block.uri))
                    is SerializableCameraBlock -> block.copy(uri = repair(block.uri))
                    else -> block
                }
            },
    )
