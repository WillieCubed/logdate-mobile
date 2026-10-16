package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

internal class RestoreDraftReconciler(
    private val repository: JournalRepository,
    private val warnings: MutableList<String>,
    private val decoder: RestoreDraftDecoder,
) {
    private data class Undo(
        val original: EditorDraft?,
        val writes: MutableList<EditorDraft> = mutableListOf(),
    )

    private val undo = linkedMapOf<Uuid, Undo>()
    private var recoveryScope: DraftRecoveryScope? = null

    suspend fun restore(
        drafts: List<ExportDraft>,
        strategy: RestoreStrategy,
    ): Int {
        recoveryScope = repository.getDraftRecoveryScope()
        var imported = 0
        for (draft in drafts) {
            check(repository.getDraftRecoveryScope() == recoveryScope) { "Draft recovery account changed" }
            val id = runCatching { Uuid.parse(draft.id) }.getOrNull()
            if (id == null) {
                warnings.add("Skipped draft with an invalid ID")
                continue
            }
            val existing = repository.getDraftForRestore(id)
            if (existing != null) repository.ensureDraftRecoveryQueued(id, recoveryScope)
            val prior = undo[id]
            if (prior != null && existing != (prior.writes.lastOrNull() ?: prior.original)) {
                warnings.add("Kept a draft changed during archive recovery")
                continue
            }
            if (!shouldOverwrite(existing?.lastModifiedAt, draft.updatedAt, strategy)) continue
            val restored = decoder.decode(draft)
            val snapshot = undo.getOrPut(id) { Undo(existing) }
            // Include a possibly durable write in rollback even if its sync enqueue subsequently fails.
            snapshot.writes.add(restored)
            if (repository.compareAndSetDraftForRestore(id, existing, restored, recoveryScope)) {
                imported++
            } else {
                snapshot.writes.removeAt(snapshot.writes.lastIndex)
                warnings.add("Kept a draft changed during archive recovery")
            }
        }
        check(repository.getDraftRecoveryScope() == recoveryScope) { "Draft recovery account changed" }
        return imported
    }

    suspend fun rollback() {
        for ((id, snapshot) in undo) {
            runCatching {
                for (applied in snapshot.writes.asReversed()) {
                    if (repository.compareAndSetDraftForRestore(id, applied, snapshot.original, recoveryScope)) break
                }
            }.onFailure { Napier.e("Rollback: draft recovery could not be reverted", it) }
        }
    }
}
