package app.logdate.client.media

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Cleanup callback for an import that failed after the media manager returned a URI.
 *
 * The URI is not an ownership token: the current Android importer returns an app-private
 * canonical URI that may have deduplicated against an existing note, draft, or cover.
 * Only the import source's unique staging file can be discarded here. Managed originals stay
 * available until an explicit, reference-aware deletion can prove they have no remaining owner.
 */
class AndroidManagedMediaDiscarder(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") mediaManager: MediaManager,
    @Suppress("UNUSED_PARAMETER") ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun discard(
        @Suppress("UNUSED_PARAMETER") managedUri: String,
    ) = Unit
}
