package app.logdate.core.data.location

import app.logdate.core.database.dao.LocationHistoryDao
import app.logdate.model.Location
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A repository that stores user location data.
 */
interface LocationHistoryRepository {

    /**
     * Queries the location history between the given [start] and [end] times.
     *
     * @param start The start time of the query.
     * @param end The end time of the query. Defaults to the current time.
     */
    fun queryHistory(
        start: Instant,
        end: Instant = Clock.System.now(),
    ): List<Location>

    suspend fun addLocationHistoryEntry(entry: Location)

    /**
     * Removes a location history entry with the given [timestamp].
     */
    suspend fun removeLocationHistoryEntry(timestamp: Instant)

    /**
     * Removes all location history entries.
     */
    suspend fun clearLocationHistory()
}

@Singleton
class OfflineFirstLocationHistoryRepository @Inject constructor(
    private val locationHistoryDao: LocationHistoryDao,
) : LocationHistoryRepository {
    override fun queryHistory(
        start: Instant,
        end: Instant,
    ): List<Location> {
        TODO("Not yet implemented")
    }

    override suspend fun addLocationHistoryEntry(entry: Location) {
        TODO("Not yet implemented")
    }

    override suspend fun removeLocationHistoryEntry(timestamp: Instant) {
        TODO("Not yet implemented")
    }

    override suspend fun clearLocationHistory() {
        TODO("Not yet implemented")
    }

}