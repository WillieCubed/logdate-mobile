package app.logdate.client.media.audio

import app.logdate.client.repository.journals.EntryDraftRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.uuid.Uuid

interface RecordingDraftLifecycle {
    val draftId: Uuid? get() = null

    suspend fun prepare(
        blockId: Uuid,
        path: String,
    )

    suspend fun checkpoint(path: String)

    suspend fun complete(
        path: String,
        durationMs: Long,
        quietly: Boolean,
    ): Uuid?
}

class RecordingDraftSession(
    private val drafts: EntryDraftRepository,
    private val currentOwner: suspend () -> String,
) : RecordingDraftLifecycle {
    override var draftId: Uuid? = null
        private set

    private var recordingBlock: Uuid? = null
    private var recordingOwner: String? = null

    override suspend fun prepare(
        blockId: Uuid,
        path: String,
    ) {
        val owner = currentOwner()
        withTimeout(10_000) {
            drafts.getDrafts().first { entries -> entries.any { draft -> draft.pendingMedia.any { it.blockId == blockId } } }
        }
        check(currentOwner() == owner) { "Recording account changed" }
        val preparedDraft = checkNotNull(drafts.checkpointRecording(blockId, path, owner)) { "Recording has no durable draft" }
        draftId = preparedDraft
        recordingBlock = blockId
        recordingOwner = owner
    }

    override suspend fun checkpoint(path: String) {
        val block = recordingBlock ?: return
        val owner = recordingOwner ?: return
        check(currentOwner() == owner) { "Recording account changed" }
        check(drafts.checkpointRecording(block, path, owner) != null) { "Recording draft no longer exists" }
    }

    override suspend fun complete(
        path: String,
        durationMs: Long,
        quietly: Boolean,
    ): Uuid? {
        val block = recordingBlock ?: return null
        val owner = recordingOwner ?: return null
        check(currentOwner() == owner) { "Recording account changed" }
        return drafts.completeRecording(block, path, durationMs, owner, quietly)
    }
}
