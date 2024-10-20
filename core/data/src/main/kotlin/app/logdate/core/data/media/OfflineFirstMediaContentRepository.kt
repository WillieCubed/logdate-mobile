package app.logdate.core.data.media

import app.logdate.core.database.dao.UserMediaDao
import app.logdate.model.LibraryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstMediaContentRepository @Inject constructor(
    databaseMediaDataSource: DatabaseMediaDataSource,
    localMediaDataSource: LocalStorageMediaDataSource,
) : LibraryContentRepository {
    override val allItemsObserved: Flow<List<LibraryItem>> = localMediaDataSource.observeUris()
        .map { uris ->
//            uris.map { uri ->
//                LibraryItem(
//                    id = uri,
//                    uri = uri,
//                    creationTimestamp = ,
//                    title = uri.substringAfterLast('/'),
//                )
//            }
            listOf()
        }
}

/**
 * A data source for media content stored in a database.
 */
class DatabaseMediaDataSource @Inject constructor(
    private val userMediaDao: UserMediaDao,
) {
    /**
     * Observe a list of URIs
     */
    fun observeUris(): Flow<List<String>> {
        return TODO()
    }
}

/**
 * A local data source that loads user's media content from the device's storage.
 */
class LocalStorageMediaDataSource @Inject constructor() {
    fun observeUris(): Flow<List<String>> {
        return TODO()
    }
}