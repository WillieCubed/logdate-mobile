package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** One request's permission; never a global policy change or a transferable account setting. */
private class MobileDataConsent(
    val scope: UploadScope,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<MobileDataConsent>
}

internal suspend fun <T> withMobileDataConsent(
    scope: UploadScope,
    block: suspend () -> T,
): T = withContext(MobileDataConsent(scope)) { block() }

internal suspend fun mediaSyncAllowed(
    mode: DataUsageMode,
    scope: UploadScope?,
): Boolean =
    mode.shouldSyncMedia() ||
        (mode == DataUsageMode.Conservative && scope != null && coroutineContext[MobileDataConsent]?.scope == scope)
