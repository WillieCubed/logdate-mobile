package app.logdate.client.domain.restore

import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.mediaRefOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

internal class ArchiveMediaRepairSession(
    private val notes: JournalNotesRepository,
    private val journals: JournalRepository,
    private val importer: MediaImporter,
    private val noteScope: JournalMergeScope?,
    private val draftScope: DraftRecoveryScope?,
) {
    suspend fun requireScope() {
        check(notes.getNoteRecoveryScope() == noteScope && journals.getDraftRecoveryScope() == draftScope) {
            "Archive media recovery account changed"
        }
    }

    suspend fun stillPending(asset: UnavailableMediaAsset): Boolean {
        val id = runCatching { Uuid.parse(asset.entityId) }.getOrNull() ?: return false
        val pending =
            if (asset.blockId == null) {
                val note = notes.getNoteById(id)
                note?.mediaRefOrNull() == asset.reference && (asset.kind == null || note.type.name.lowercase() == asset.kind)
            } else {
                val block = journals.getDraftForRestore(id)?.blocks?.firstOrNull { it.id.toString() == asset.blockId }
                block?.archiveMediaReference(asset.role) == asset.reference &&
                    (asset.kind == null || block.archiveMediaKind() == asset.kind)
            }
        currentCoroutineContext().ensureActive()
        return pending
    }

    suspend fun import(asset: UnavailableMediaAsset): String? {
        val local =
            try {
                asset.recoveredReference?.takeIf { importer.isAvailable(it) }
                    ?: asset.sourceReference?.takeIf { importer.isAvailable(it) }
                    ?: asset.archivePath?.let { importer.importMedia(it) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        val reference =
            local ?: try {
                importer.recoverMedia(asset)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        requireScope()
        return reference
    }

    suspend fun apply(
        asset: UnavailableMediaAsset,
        reference: String,
    ): Boolean {
        repeat(3) {
            requireScope()
            if (!stillPending(asset)) return false
            val id = Uuid.parse(asset.entityId)
            val applied =
                if (asset.blockId == null) {
                    val note = notes.getNoteById(id)
                    note != null &&
                        note.mediaRefOrNull() == asset.reference &&
                        notes.compareAndSetNoteForRestore(note, note.withRecoveredOriginal(reference), noteScope)
                } else {
                    applyDraft(id, asset, reference)
                }
            requireScope()
            if (applied) return true
        }
        return false
    }

    private suspend fun applyDraft(
        id: Uuid,
        asset: UnavailableMediaAsset,
        reference: String,
    ): Boolean {
        val draft = journals.getDraftForRestore(id) ?: return false
        val block = draft.blocks.firstOrNull { it.id.toString() == asset.blockId } ?: return false
        if (block.archiveMediaReference(asset.role) != asset.reference) return false
        val replacement =
            draft.copy(
                blocks =
                    draft.blocks.map {
                        if (it.id ==
                            block.id
                        ) {
                            it.withRecoveredAsset(asset.role, reference)
                        } else {
                            it
                        }
                    },
            )
        return journals.compareAndSetDraftForRestore(id, draft, replacement, draftScope)
    }
}
