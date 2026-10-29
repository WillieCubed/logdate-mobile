package app.logdate.client.data.notes.drafts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orders platform admission and media ownership before the repository's mutation mutex. */
internal interface EntryDraftMutationGuard {
    suspend fun <T> withDraftMutation(
        admitContent: Boolean,
        block: suspend () -> T,
    ): T
}

internal suspend fun <T> LocalEntryDraftStore.withEntryDraftMutation(
    mutex: Mutex,
    admitContent: Boolean,
    block: suspend () -> T,
): T =
    if (this is EntryDraftMutationGuard) {
        // Recovery already owns delivery admission; do not reacquire its non-reentrant gate.
        withDraftMutation(admitContent) { mutex.withLock { block() } }
    } else {
        mutex.withLock { block() }
    }
