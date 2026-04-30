package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.SetEntryDraftPendingMediaUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Result of loading a draft, containing the converted blocks and the draft ID.
 */
data class LoadedDraft(
    val blocks: List<EntryBlockUiState>,
    val draftId: Uuid,
)

/**
 * Manages all draft operations: auto-save, load, delete, and cleanup.
 */
class DraftManager(
    private val updateEntryDraft: UpdateEntryDraftUseCase,
    private val createEntryDraft: CreateEntryDraftUseCase,
    private val fetchEntryDraft: FetchEntryDraftUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
    private val deleteAllDraftsUseCase: DeleteAllDraftsUseCase,
    private val cleanupExpiredDraftsUseCase: CleanupExpiredDraftsUseCase,
    private val setPendingMedia: SetEntryDraftPendingMediaUseCase,
) {
    /**
     * Auto-saves the current entry state as a draft.
     *
     * Persistable blocks (Ready audio, text with content, finalized images, etc.)
     * are written as `notes`. Audio blocks in a non-Ready, non-Empty state are
     * written as `pendingMedia` so the recording survives process death and the
     * editor can recover it on relaunch.
     *
     * @return The draft ID if a draft was created or updated, null if skipped.
     */
    suspend fun autoSave(state: EditorState): Uuid? {
        val now = Clock.System.now()
        val notes = mutableListOf<JournalNote>()
        val pendingMedia = mutableListOf<PendingMediaRecord>()
        for (block in state.blocks) {
            if (state.isReadOnly(block.id)) continue
            if (block.hasContent()) {
                block.toJournalNote()?.let(notes::add)
            }
            if (block is AudioBlockUiState) {
                block.toPendingMediaRecord(now)?.let(pendingMedia::add)
            }
        }

        if (notes.isEmpty() && pendingMedia.isEmpty()) {
            Napier.d("Skip autosave: no editable content")
            return null
        }

        val draftId =
            when (val draft = state.draftState) {
                is DraftState.Active -> {
                    updateEntryDraft(draft.id, notes)
                    setPendingMedia(draft.id, pendingMedia)
                    draft.id
                }
                DraftState.None -> {
                    val newId = createEntryDraft(notes)
                    if (pendingMedia.isNotEmpty()) {
                        setPendingMedia(newId, pendingMedia)
                    }
                    newId
                }
            }

        Napier.d("Auto-saved draft: $draftId (${notes.size} notes, ${pendingMedia.size} pending)")
        return draftId
    }

    /**
     * Loads a draft by ID and converts its notes and pending media to UI blocks.
     *
     * Pending media records reconstruct as audio blocks in [AudioCaptureState.Stopping]
     * — the original recording session is gone after process death, so the editor
     * surfaces them as "needs recovery" rather than continuing to record. A
     * follow-up step (orphan recovery) validates the file on disk and either
     * promotes the block to [AudioCaptureState.Ready] or marks it failed.
     */
    suspend fun loadDraft(draftId: Uuid): Result<LoadedDraft> =
        try {
            val result = fetchEntryDraft(draftId).first()
            result.map { draft ->
                val noteBlocks = draft.notes.map { it.toDomainBlock() }
                val pendingBlocks = draft.pendingMedia.mapNotNull { it.toBlock() }
                LoadedDraft(
                    blocks = noteBlocks + pendingBlocks,
                    draftId = draft.id,
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Deletes a single draft by ID.
     */
    suspend fun deleteDraft(draftId: Uuid): Result<Unit> =
        try {
            deleteEntryDraft(draftId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Deletes all drafts atomically.
     */
    suspend fun deleteAllDrafts(): Result<Unit> =
        try {
            deleteAllDraftsUseCase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Removes expired drafts.
     *
     * @return The number of drafts deleted.
     */
    suspend fun cleanupExpired(): Int = cleanupExpiredDraftsUseCase()
}

/**
 * Builds the [PendingMediaRecord] list for a draft from the editor's blocks.
 *
 * Read-only blocks (already-persisted entries loaded into the editor) are
 * skipped. So are Empty and Ready audio blocks — Empty has no content, and
 * Ready becomes a [JournalNote.Audio] in the draft's notes list.
 */
private fun AudioBlockUiState.toPendingMediaRecord(now: Instant): PendingMediaRecord? {
    val filePath =
        when (val capture = captureState) {
            is AudioCaptureState.Recording -> capture.filePath
            is AudioCaptureState.Stopping -> capture.filePath
            AudioCaptureState.Empty,
            is AudioCaptureState.Ready,
            is AudioCaptureState.Failed,
            -> return null
        }
    return PendingMediaRecord(
        blockId = id,
        mediaType = PendingMediaType.AUDIO,
        createdAt = now,
        filePath = filePath,
    )
}

/**
 * Reconstructs an [EntryBlockUiState] from a [PendingMediaRecord].
 *
 * Recording / Stopping states from a previous session are surfaced as
 * [AudioCaptureState.Stopping] — the recorder is no longer active so we cannot
 * resume in-progress recording, but the file may exist on disk and be valid.
 */
private fun PendingMediaRecord.toBlock(): EntryBlockUiState? =
    when (mediaType) {
        PendingMediaType.AUDIO ->
            AudioBlockUiState(
                id = blockId,
                timestamp = createdAt,
                captureState = AudioCaptureState.Stopping(filePath = filePath),
            )
    }
