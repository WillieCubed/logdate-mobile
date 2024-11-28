package app.logdate.client.data.rewind

import app.logdate.client.database.dao.rewind.CachedRewindDao
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * A [RewindRepository] that uses a local database as the single source of truth.
 */
class OfflineFirstRewindRepository(
    private val cachedRewindDao: CachedRewindDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RewindRepository {
    override fun getAllRewinds(): Flow<List<Rewind>> = flow {
        // Get all rewinds from the cache
        // This will be a Flow that emits the rewinds once
        // and then completes
        emit(emptyList())
    }

    override fun getRewind(uid: Uuid): Flow<Rewind> {
        return emptyFlow()
    }

    override fun getRewindBetween(
        start: Instant,
        end: Instant,
    ): Flow<Rewind?> {
        if (rewindExists(start, end)) {
            // Return the rewind from the cache
            // This will be a Flow that emits the rewind once
            // and then completes
            return TODO()
        }
        // First check if the rewind is available in the cache
        // If it is, return it
        // If it is not, generate the rewind and save it to the cache
        return flow {
            emit(null)
        }
    }

    override suspend fun isRewindAvailable(
        start: Instant,
        end: Instant,
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun createRewind(start: Instant, end: Instant): Rewind {
        TODO()
    }

    private fun rewindExists(start: Instant, end: Instant): Boolean {
        return false
    }
}

/**
 * Paginates by date.
 */
//class DateSortedRewindPagingSource @Inject constructor(
//
//) : PagingSource<Instant, RewindLoadResult>() {
//    override fun getRefreshKey(state: PagingState<Instant, RewindLoadResult>): Instant? {
//    }
//
//    override suspend fun load(params: LoadParams<Instant>): LoadResult<Instant, RewindLoadResult> {
//        return try {
//
//        } catch (e: IOException) {
//            RewindLoadResult.Error(e)
//        }
//    }
//
//}

sealed interface RewindLoadResult {
    data class Success(val rewind: Rewind) : RewindLoadResult
    data class Error(val throwable: Throwable) : RewindLoadResult
}