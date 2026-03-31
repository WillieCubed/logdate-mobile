package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchContentType

/**
 * Filters that narrow universal search results.
 *
 * @property contentTypes Restrict results to specific entity types. Null means all types.
 * @property maxResults Maximum number of results to return.
 */
data class SearchFilters(
    val contentTypes: Set<SearchContentType>? = null,
    val maxResults: Int = 50,
) {
    companion object {
        /** Filters with no restrictions — returns all content types, up to 50 results. */
        val Default = SearchFilters()
    }
}
