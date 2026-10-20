package app.logdate.client.sync

import app.logdate.client.sync.metadata.UploadScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** The owner selected for one full run, retained while its phases wait for their locks. */
internal class FullSyncScope(
    val owner: UploadScope?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FullSyncScope>
}

internal suspend fun SyncTokenRefresher.isFullSyncScopeCurrent(): Boolean =
    coroutineContext[FullSyncScope]?.let { it.owner == currentUploadScope() } ?: true
