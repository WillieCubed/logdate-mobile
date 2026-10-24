package app.logdate.client.data.restore

import app.logdate.client.data.journals.ArchiveRecoveryReceipts
import app.logdate.client.data.journals.LocalDeletionType
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import kotlin.uuid.Uuid

internal data class RetainedTranscriptionSnapshot(
    val row: TranscriptionEntity,
    val segments: List<TranscriptionSegmentEntity>,
)

internal class RetainedTranscriptionRecoveryPersistence(
    private val transcripts: TranscriptionDao,
    private val audio: AudioNoteDao,
    private val transactions: SyncTransactionManager,
    private val metadata: SyncMetadataService,
    private val receipts: ArchiveRecoveryReceipts,
    private val currentScope: suspend () -> JournalMergeScope?,
) {
    suspend fun snapshot(noteId: Uuid): RetainedTranscriptionSnapshot? =
        transcripts.getTranscriptionByNoteId(noteId)?.let { RetainedTranscriptionSnapshot(it, transcripts.getSegmentsByNoteId(noteId)) }

    suspend fun wasApplied(
        scope: JournalMergeScope,
        recoveryId: String,
        noteId: Uuid,
    ): Boolean = receipts.contains(scope, "TRANSCRIPTION", recoveryId, noteId.toString())

    suspend fun wasDeleted(
        scope: JournalMergeScope,
        noteId: Uuid,
    ): Boolean = receipts.containsLocalDeletion(scope, LocalDeletionType.TRANSCRIPTION, noteId.toString())

    suspend fun compareAndSet(
        expected: RetainedTranscriptionSnapshot?,
        replacement: RetainedTranscriptionSnapshot,
        expectedAudio: AudioNoteEntity,
        scope: JournalMergeScope,
        recoveryId: String,
        ensureCurrent: suspend () -> Unit,
    ): Boolean {
        val noteId = replacement.row.noteId
        require(expectedAudio.uid == noteId && replacement.segments.all { it.noteId == noteId })
        require(
            replacement.segments
                .map { it.segmentId }
                .distinct()
                .size == replacement.segments.size,
        )
        return transactions.withTransaction {
            ensureCurrent()
            check(currentScope() == scope) { "Transcript recovery account changed" }
            val liveAudio = audio.findNoteOneOff(noteId)
            if (liveAudio != expectedAudio || liveAudio.deletedAt != null) return@withTransaction false
            val current = snapshot(noteId)
            if (receipts.containsLocalDeletion(scope, LocalDeletionType.TRANSCRIPTION, noteId.toString())) {
                return@withTransaction false
            }
            if (receipts.contains(scope, "TRANSCRIPTION", recoveryId, noteId.toString())) {
                // A committed application survives checkpoint loss. Later edits/deletions remain authoritative.
                return@withTransaction current != null
            }
            if (current != expected) return@withTransaction false
            val collision = transcripts.getTranscriptionById(replacement.row.id)
            if (collision != null && collision.noteId != noteId) return@withTransaction false
            if (!metadata.enqueueRestoreMutation(
                    noteId.toString(),
                    EntityType.NOTE,
                    PendingOperation.UPDATE,
                    UploadScope(scope.ownerId, scope.serverOrigin),
                )
            ) {
                return@withTransaction false
            }
            if (current == null) {
                transcripts.insertTranscription(replacement.row)
            } else {
                check(transcripts.updateTranscription(replacement.row) == 1)
            }
            transcripts.replaceSegmentsForNote(noteId, replacement.segments)
            receipts.record(scope, "TRANSCRIPTION", recoveryId, noteId.toString())
            ensureCurrent()
            check(currentScope() == scope) { "Transcript recovery account changed" }
            true
        }
    }
}
