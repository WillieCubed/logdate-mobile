package app.logdate.client.data.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPlatformSearchRepository(
    private val appSearchIndexManager: AndroidPlatformSearchIndexManager,
    private val roomSearchRepository: OfflineFirstSearchRepository,
) : SearchRepository {
    init {
        appSearchIndexManager.ensureStarted()
    }

    override fun search(query: String): Flow<List<SearchResult>> =
        observeAppSearch(
            query = query,
            limit = DEFAULT_SEARCH_LIMIT,
            fallback = { roomSearchRepository.search(query) },
        )

    override fun searchWithLimit(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> =
        observeAppSearch(
            query = query,
            limit = limit,
            fallback = { roomSearchRepository.searchWithLimit(query, limit) },
        )

    override fun searchWithSnippets(query: String): Flow<List<SearchResult>> =
        observeAppSearch(
            query = query,
            limit = DEFAULT_SEARCH_LIMIT,
            fallback = { roomSearchRepository.searchWithSnippets(query) },
        )

    override fun searchRanked(
        query: String,
        limit: Int,
    ): Flow<List<SearchResult>> =
        observeAppSearch(
            query = query,
            limit = limit,
            fallback = { roomSearchRepository.searchRanked(query, limit) },
        )

    override fun searchInJournal(
        query: String,
        journalId: kotlin.uuid.Uuid,
        limit: Int,
    ): Flow<List<SearchResult>> = roomSearchRepository.searchInJournal(query, journalId, limit)

    private fun observeAppSearch(
        query: String,
        limit: Int,
        fallback: () -> Flow<List<SearchResult>>,
    ): Flow<List<SearchResult>> {
        val preparedQuery = prepareFtsQuery(query) ?: return flowOf(emptyList())
        if (!appSearchIndexManager.isSupported() || preparedQuery.usesExplicitSyntax) {
            return fallback()
        }

        return appSearchIndexManager
            .observeIndexedGeneration()
            .distinctUntilChanged()
            .mapLatest {
                val results =
                    runCatching {
                        appSearchIndexManager.search(
                            preparedQuery = preparedQuery,
                            limit = limit,
                        )
                    }.getOrElse { error ->
                        Napier.w("Android AppSearch query failed; falling back to Room search", error)
                        return@mapLatest fallback().first()
                    }

                if (results.isNotEmpty()) {
                    results
                } else {
                    fallback().first()
                }
            }.catch { error ->
                Napier.w("Android AppSearch flow failed; falling back to Room search", error)
                emit(fallback().first())
            }
    }

    private companion object {
        const val DEFAULT_SEARCH_LIMIT = 50
    }
}
