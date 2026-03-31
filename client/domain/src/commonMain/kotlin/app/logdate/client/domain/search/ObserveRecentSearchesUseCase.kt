package app.logdate.client.domain.search

import app.logdate.client.repository.search.RecentSearchesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing and recording recent search queries.
 */
class ObserveRecentSearchesUseCase(
    private val recentSearchesRepository: RecentSearchesRepository,
) {
    /**
     * Observes the list of recent searches, most recent first.
     */
    operator fun invoke(): Flow<List<String>> = recentSearchesRepository.observeRecentSearches()

    /**
     * Records a completed search query for future suggestions.
     */
    suspend fun record(query: String) {
        if (query.isNotBlank()) {
            recentSearchesRepository.addRecentSearch(query.trim())
        }
    }

    /**
     * Clears all recent searches.
     */
    suspend fun clear() {
        recentSearchesRepository.clearRecentSearches()
    }
}
