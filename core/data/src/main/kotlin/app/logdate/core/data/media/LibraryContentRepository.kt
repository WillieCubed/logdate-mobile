package app.logdate.core.data.media

import app.logdate.model.LibraryItem
import kotlinx.coroutines.flow.Flow

/**
 * A repository for BLOB-like user data.
 */
interface LibraryContentRepository {
    /**
     * This should be observed
     */
    val allItemsObserved: Flow<List<LibraryItem>>
}