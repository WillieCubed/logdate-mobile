package app.logdate.client.data.notes

import app.logdate.client.data.journals.NativeDraftMediaQuarantine
import app.logdate.client.data.notes.drafts.NativeEntryDraftMediaQuarantine
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.restore.NativeRestoreDeliveryGate
import app.logdate.client.media.storage.MediaReference
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
    private val deliveryGate: NativeRestoreDeliveryGate = ImmediateNativeRestoreDeliveryGate,
    private val entryDrafts: NativeEntryDraftMediaQuarantine? = null,
    private val mediaExists: suspend (String) -> Boolean,
) {
    suspend fun run(isCurrent: () -> Boolean): Boolean =
        deliveryGate.withLock {
            var availabilityChecksComplete = true
            val quarantine: suspend (String) -> String? = { reference ->
                quarantined(reference) { availabilityChecksComplete = false }
            }
            check(isCurrent()) { "Native media delivery changed" }
            transactions.withTransaction {
                database.imageNoteDao().getAll().forEach { note ->
                    quarantine(note.contentUri)?.let { database.imageNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it) }
                }
                database.audioNoteDao().getAll().forEach { note ->
                    quarantine(note.contentUri)?.let {
                        database.audioNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it)
                        transcriptions.rebindMediaReference(note.uid, note.contentUri, it)
                    }
                }
                database.videoNoteDao().getAll().forEach { note ->
                    quarantine(note.contentUri)?.let { database.videoNoteDao().updateContentUriIfUnchanged(note.uid, note.contentUri, it) }
                }
                database.journalDao().getAll().forEach { journal ->
                    journal.coverImageUri?.let { previous ->
                        quarantine(previous)?.let { database.journalDao().updateCoverImageUriIfUnchanged(journal.id, previous, it) }
                    }
                }
                check(isCurrent()) { "Native media delivery changed" }
            }
            drafts.run(isCurrent, quarantine)
            val currentDraftsComplete = entryDrafts?.run(isCurrent, quarantine) ?: true
            check(isCurrent()) { "Native media delivery changed" }
            availabilityChecksComplete && currentDraftsComplete
        }

    private suspend fun quarantined(
        reference: String,
        onUnknown: () -> Unit,
    ): String? {
        if (UnavailableMediaReferences.requiresNativeQuarantine(reference)) {
            return UnavailableMediaReferences.nativeRestore(reference)
        }
        if (UnavailableMediaReferences.isUnavailable(reference)) return null
        if (MediaReference.parse(reference) !is MediaReference.Owned &&
            MediaReference.parse(reference) !is MediaReference.LocalFile
        ) {
            return null
        }
        val available =
            try {
                mediaExists(reference)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onUnknown()
                // Keep the source reference inside the unavailable marker so a later retry can
                // resolve it, while preventing restored UI from treating an unchecked URI as
                // present during startup.
                return UnavailableMediaReferences.nativeRestore(reference)
            }
        return if (available) null else UnavailableMediaReferences.nativeRestore(reference)
    }
}

private object ImmediateNativeRestoreDeliveryGate : NativeRestoreDeliveryGate {
    override suspend fun <T> withLock(block: suspend () -> T): T = block()
}

internal suspend fun quarantineNativeDraft(
    draft: EditorDraft,
    quarantine: suspend (String) -> String?,
): EditorDraft {
    suspend fun repair(reference: String?): String? = reference?.let { quarantine(it) ?: it }
    val blocks =
        draft.blocks.map { block ->
            when (block) {
                is SerializableImageBlock -> block.copy(uri = repair(block.uri))
                is SerializableVideoBlock -> block.copy(uri = repair(block.uri), thumbnailUri = repair(block.thumbnailUri))
                is SerializableAudioBlock -> block.copy(uri = repair(block.uri))
                is SerializableCameraBlock -> block.copy(uri = repair(block.uri))
                else -> block
            }
        }
    return draft.copy(blocks = blocks)
}
