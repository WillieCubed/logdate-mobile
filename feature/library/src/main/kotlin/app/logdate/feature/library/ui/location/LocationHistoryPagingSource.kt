package app.logdate.feature.library.ui.location

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.logdate.core.data.location.LocationHistoryRepository
import app.logdate.model.Location
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A data source for loading location history into a pager.
 */
class LocationHistoryPagingSource(
    private val locationHistoryRepository: LocationHistoryRepository,
    private val query: LocationQueryData,
) : PagingSource<Instant, Location>() {

    /**
     * The key is an instant representing the start boundary used to return location logs.
     */
    override fun getRefreshKey(state: PagingState<Instant, Location>): Instant? {
        TODO("Not yet implemented")
    }

    override suspend fun load(params: LoadParams<Instant>): LoadResult<Instant, Location> {
        val page = params.key ?: Instant.DISTANT_PAST
        val pageSize = params.loadSize
        val start = query.start
        val end = query.end
        try {
            val locations = locationHistoryRepository.queryHistory(start, end)
            return LoadResult.Page(
                data = locations,
                prevKey = null,
                // Increment by a day
                nextKey = page + (24 * 60).toDuration(DurationUnit.MINUTES),
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }
}

/**
 * Wrapper object for properties used in a location query.
 */
data class LocationQueryData(
    val start: Instant,
    val end: Instant,
)