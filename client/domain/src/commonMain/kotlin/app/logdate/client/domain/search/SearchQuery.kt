package app.logdate.client.domain.search

import kotlin.jvm.JvmInline

/**
 * A search query value type.
 *
 * Currently wraps a text string. Designed to be upgradeable to a sealed interface
 * with multimodal variants (e.g., image queries) when the search backend supports them.
 */
@JvmInline
value class SearchQuery(
    val text: String,
) {
    /** Whether the query text is blank (empty or whitespace-only). */
    val isBlank: Boolean get() = text.isBlank()

    /** Whether the query text is non-empty. */
    val isNotEmpty: Boolean get() = text.isNotEmpty()

    companion object {
        /** A query with no text. */
        val Empty = SearchQuery("")
    }
}
