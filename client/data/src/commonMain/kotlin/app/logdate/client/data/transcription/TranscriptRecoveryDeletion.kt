package app.logdate.client.data.transcription

import app.logdate.client.data.journals.LocalDeletionType
import app.logdate.client.data.journals.RoomArchiveRecoveryReceipts
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.shared.config.LogDateConfigRepository
import kotlin.uuid.Uuid

interface TranscriptRecoveryDeletions {
    suspend fun contains(noteId: Uuid): Boolean

    suspend fun record(noteId: Uuid)

    suspend fun clear(noteId: Uuid)

    object None : TranscriptRecoveryDeletions {
        override suspend fun contains(noteId: Uuid) = false

        override suspend fun record(noteId: Uuid) = Unit

        override suspend fun clear(noteId: Uuid) = Unit
    }
}

/** Mutations join the caller's Room transaction, including its transcript/segment deletion. */
internal class ScopedTranscriptRecoveryDeletions(
    database: LogDateDatabase,
    private val owner: CanonicalOwnerProvider,
    private val config: LogDateConfigRepository,
) : TranscriptRecoveryDeletions {
    private val receipts = RoomArchiveRecoveryReceipts(database.downloadInboxDao())

    private suspend fun scope() = JournalMergeScope(owner.getCanonicalOwnerId(), config.getCurrentBackendUrl().trimEnd('/'))

    override suspend fun contains(noteId: Uuid): Boolean {
        val captured = scope()
        val deleted = receipts.containsLocalDeletion(captured, LocalDeletionType.TRANSCRIPTION, noteId.toString())
        return deleted || scope() != captured
    }

    override suspend fun record(noteId: Uuid) {
        val captured = scope()
        receipts.recordLocalDeletion(captured, LocalDeletionType.TRANSCRIPTION, noteId.toString())
        check(scope() == captured) { "Transcript deletion account changed" }
    }

    override suspend fun clear(noteId: Uuid) {
        val captured = scope()
        receipts.clearLocalDeletion(captured, LocalDeletionType.TRANSCRIPTION, noteId.toString())
        check(scope() == captured) { "Transcript request account changed" }
    }
}
