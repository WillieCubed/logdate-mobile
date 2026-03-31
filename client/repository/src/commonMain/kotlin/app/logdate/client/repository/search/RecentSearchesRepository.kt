package app.logdate.client.repository.search

import kotlinx.coroutines.flow.Flow

/**
 * Persistence layer for recent search queries.
 *
 * Stores the user's most recent search queries so they can be displayed
 * as suggestions when the search bar is empty.
 */
interface RecentSearchesRepository {
    /**
     * Observes the list of recent searches, most recent first.
     */
    fun observeRecentSearches(): Flow<List<String>>

    /**
     * Records a search query. Deduplicates and caps at a fixed limit.
     */
    suspend fun addRecentSearch(query: String)

    /**
     * Removes a specific query from the recent searches list.
     */
    suspend fun removeRecentSearch(query: String)

    /**
     * Clears all recent searches.
     */
    suspend fun clearRecentSearches()
}
