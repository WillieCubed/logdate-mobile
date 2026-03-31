package app.logdate.client.domain.fakes

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Minimal fake for testing use cases that depend on [SearchRepository].
 */
class FakeSearchRepository(
    initialResults: List<SearchResult> = emptyList(),
) : SearchRepository {
    private val resultsFlow = MutableStateFlow(initialResults)

    fun setResults(results: List<SearchResult>) {
        resultsFlow.value = results
    }

    override fun search(query: String): Flow<List<SearchResult>> =
        resultsFlow.map { results ->
            if (query.isBlank()) {
                emptyList()
            } else {
                results.filter { it.content.contains(query, ignoreCase = true) }
            }
        }

    override fun searchWithLimit(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> = search(query).map { it.take(limit) }

    override fun searchWithSnippets(query: String): Flow<List<SearchResult>> = search(query)

    override fun searchRanked(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> = search(query).map { it.take(limit) }
}
