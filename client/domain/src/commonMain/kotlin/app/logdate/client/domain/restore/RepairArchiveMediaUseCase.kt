package app.logdate.client.domain.restore

import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository

/** Repairs attachment references without replaying archive text, timestamps, or memberships. */
class RepairArchiveMediaUseCase(
    private val notes: JournalNotesRepository,
    private val journals: JournalRepository,
) {
    suspend fun repair(
        pending: List<UnavailableMediaAsset>,
        importer: MediaImporter,
        onPendingChanged: ((List<UnavailableMediaAsset>) -> Unit)? = null,
        expectedNoteScope: JournalMergeScope? = null,
    ): ArchiveMediaRepairResult {
        val noteScope = notes.getNoteRecoveryScope()
        check(expectedNoteScope == null || noteScope == expectedNoteScope) { "Archive media recovery account changed" }
        val draftScope = journals.getDraftRecoveryScope()
        check(
            expectedNoteScope == null ||
                draftScope == null ||
                draftScope.outboxOwnerId == expectedNoteScope.ownerId &&
                draftScope.outboxServerOrigin == expectedNoteScope.serverOrigin,
        ) { "Archive media recovery draft account changed" }
        val session = ArchiveMediaRepairSession(notes, journals, importer, noteScope, draftScope)
        val remaining = pending.toMutableList()
        var imported = 0
        var index = 0
        while (index < remaining.size) {
            session.requireScope()
            var asset = remaining[index]
            if (!session.stillPending(asset)) {
                remaining.removeAt(index)
                onPendingChanged?.invoke(remaining.toList())
                continue
            }
            val reference = session.import(asset)
            if (reference == null) {
                index++
                continue
            }
            asset = asset.copy(recoveredReference = reference)
            remaining[index] = asset
            onPendingChanged?.invoke(remaining.toList())
            val repaired = session.apply(asset, reference)
            if (repaired || !session.stillPending(asset)) {
                if (repaired) imported++
                remaining.removeAt(index)
                onPendingChanged?.invoke(remaining.toList())
            } else {
                index++
            }
        }
        return ArchiveMediaRepairResult(remaining, imported)
    }
}

data class ArchiveMediaRepairResult(
    val pending: List<UnavailableMediaAsset>,
    val imported: Int,
)
