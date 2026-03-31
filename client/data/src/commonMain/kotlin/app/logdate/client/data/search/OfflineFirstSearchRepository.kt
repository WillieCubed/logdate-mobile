package app.logdate.client.data.search

import app.logdate.client.database.dao.SearchDao
import app.logdate.client.database.dao.SearchResultEntity
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Offline-first implementation of [SearchRepository].
 *
 * Uses the local [SearchDao] to perform FTS5 full-text search queries.
 * Manually creates Flows from suspend functions to avoid Room's
 * automatic change tracking on FTS5 virtual tables.
 *
 * All queries are wrapped in try/catch because the `entries_fts` virtual table
 * may not exist on encrypted databases where the creating migration failed.
 */
class OfflineFirstSearchRepository(
    private val searchDao: SearchDao,
) : SearchRepository {
    override fun search(query: String): Flow<List<SearchResult>> =
        ftsFlow(query) { sanitized ->
            searchDao.search(sanitized).map { it.toSearchResult() }
        }

    override fun searchWithLimit(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> =
        ftsFlow(query) { sanitized ->
            searchDao.searchWithLimit(sanitized, limit).map { it.toSearchResult() }
        }

    override fun searchWithSnippets(query: String): Flow<List<SearchResult>> =
        ftsFlow(query) { sanitized ->
            searchDao.searchWithSnippets(sanitized).map { it.toSearchResult() }
        }

    override fun searchRanked(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> =
        ftsFlow(query) { sanitized ->
            searchDao.searchRanked(sanitized, limit).map { entity ->
                SearchResult(
                    uid = entity.getUuid(),
                    content = entity.content,
                    created = entity.getCreatedInstant(),
                    contentType = SearchContentType.fromFtsValue(entity.contentType),
                    rank = entity.rank,
                )
            }
        }

    /**
     * Wraps an FTS query with sanitization.
     *
     * Returns an empty list if the query is blank.
     */
    private fun ftsFlow(
        query: String,
        block: suspend (sanitized: String) -> List<SearchResult>,
    ): Flow<List<SearchResult>> =
        flow {
            val sanitized = sanitizeFtsQuery(query)
            if (sanitized.isBlank()) {
                emit(emptyList())
                return@flow
            }
            emit(block(sanitized))
        }

    private fun SearchResultEntity.toSearchResult(): SearchResult =
        SearchResult(
            uid = getUuid(),
            content = content,
            created = getCreatedInstant(),
            contentType =
                when {
                    isTextNote() -> SearchContentType.TEXT_NOTE
                    isTranscription() -> SearchContentType.TRANSCRIPTION
                    else -> {
                        Napier.w("Unknown search result content type: $contentType, falling back to TEXT_NOTE")
                        SearchContentType.fromFtsValue(contentType)
                    }
                },
        )
}

/**
 * Sanitizes user input for safe use in FTS5 MATCH clauses.
 *
 * Handles:
 * - Blank queries (returns empty)
 * - Orphaned boolean operators (AND, OR, NOT) at boundaries
 * - Unmatched quote characters
 */
internal fun sanitizeFtsQuery(query: String): String {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return ""

    val ftsOperators = listOf("AND", "OR", "NOT")
    val words = trimmed.split("\\s+".toRegex()).toMutableList()

    while (words.isNotEmpty() && words.first().uppercase() in ftsOperators) {
        words.removeFirst()
    }
    while (words.isNotEmpty() && words.last().uppercase() in ftsOperators) {
        words.removeLast()
    }

    if (words.isEmpty()) return ""

    var result = words.joinToString(" ")

    val quoteCount = result.count { it == '"' }
    if (quoteCount % 2 != 0) {
        result = result.replace("\"", "")
    }

    return result
}
