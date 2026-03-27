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
    val isBlank: Boolean get() = text.isBlank()
    val isNotEmpty: Boolean get() = text.isNotEmpty()

    companion object {
        val Empty = SearchQuery("")
    }
}
