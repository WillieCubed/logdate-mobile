package app.logdate.client.data.search

import app.logdate.client.database.dao.SearchDao
import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Offline-first implementation of SearchRepository.
 *
 * Uses the local SearchDao to perform FTS5 full-text search queries.
 * Note: Manually creates Flows from suspend functions to avoid Room's
 * automatic change tracking on FTS5 virtual tables.
 */
class OfflineFirstSearchRepository(
    private val searchDao: SearchDao,
) : SearchRepository {

    override fun search(query: String): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val entities = searchDao.search(query)
        val results = entities.map { entity ->
            SearchResult(
                uid = entity.getUuid(),
                content = entity.content,
                created = entity.getCreatedInstant(),
                type = when {
                    entity.isTextNote() -> SearchResultType.TEXT_NOTE
                    entity.isTranscription() -> SearchResultType.TRANSCRIPTION
                    else -> SearchResultType.TEXT_NOTE
                }
            )
        }
        emit(results)
    }

    override fun searchWithLimit(query: String, limit: Int): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val entities = searchDao.searchWithLimit(query, limit)
        val results = entities.map { entity ->
            SearchResult(
                uid = entity.getUuid(),
                content = entity.content,
                created = entity.getCreatedInstant(),
                type = when {
                    entity.isTextNote() -> SearchResultType.TEXT_NOTE
                    entity.isTranscription() -> SearchResultType.TRANSCRIPTION
                    else -> SearchResultType.TEXT_NOTE
                }
            )
        }
        emit(results)
    }

    override fun searchWithSnippets(query: String): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val entities = searchDao.searchWithSnippets(query)
        val results = entities.map { entity ->
            SearchResult(
                uid = entity.getUuid(),
                content = entity.content,
                created = entity.getCreatedInstant(),
                type = when {
                    entity.isTextNote() -> SearchResultType.TEXT_NOTE
                    entity.isTranscription() -> SearchResultType.TRANSCRIPTION
                    else -> SearchResultType.TEXT_NOTE
                }
            )
        }
        emit(results)
    }
}
