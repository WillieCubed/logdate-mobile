package app.logdate.client.repository.search

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Repository for searching across all journal entries.
 *
 * Provides full-text search capabilities across text notes and voice note transcriptions.
 */
interface SearchRepository {
    /**
     * Searches all entries using full-text search.
     *
     * @param query The search query
     * @return Flow of search results ordered by relevance and date
     */
    fun search(query: String): Flow<List<SearchResult>>

    /**
     * Searches with a limit on the number of results.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return Flow of limited search results
     */
    fun searchWithLimit(query: String, limit: Int): Flow<List<SearchResult>>

    /**
     * Searches with highlighted snippets.
     *
     * @param query The search query
     * @return Flow of search results with highlighted content
     */
    fun searchWithSnippets(query: String): Flow<List<SearchResult>>
}

/**
 * A search result representing a found entry.
 */
data class SearchResult(
    val uid: Uuid,
    val content: String,
    val created: Instant,
    val type: SearchResultType,
)

/**
 * Type of search result.
 */
enum class SearchResultType {
    TEXT_NOTE,
    TRANSCRIPTION,
}
