package app.logdate.client.domain.search

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Use case for searching journals by title and description.
 *
 * Provides both a reactive Flow-based API for standalone use and a synchronous
 * filter function for use inside combine() pipelines.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchJournalsUseCase(
    private val journalRepository: JournalRepository,
) {
    /**
     * Search delay in milliseconds. Shorter than entry search (300ms)
     * because client-side filtering is instant.
     */
    private val searchDebounceMs = 200L

    /**
     * Reactively filters journals as the query changes.
     *
     * Returns all journals when query is blank. Debounced at 200ms.
     *
     * @param queryFlow Flow of search queries from the UI
     * @return Flow of filtered journals
     */
    operator fun invoke(queryFlow: Flow<SearchQuery>): Flow<List<Journal>> =
        queryFlow
            .map { it.text }
            .debounce(searchDebounceMs)
            .distinctUntilChanged()
            .flatMapLatest { queryText ->
                journalRepository.allJournalsObserved.map { journals ->
                    filterJournals(journals, SearchQuery(queryText))
                }
            }

    /**
     * Synchronous filter for use inside combine() pipelines where debouncing
     * is handled externally.
     *
     * @param journals The full list of journals to filter
     * @param query The search query; blank text returns all journals
     * @return Filtered list of journals matching the query
     */
    fun filterJournals(
        journals: List<Journal>,
        query: SearchQuery,
    ): List<Journal> {
        if (query.isBlank) return journals
        val lowerQuery = query.text.lowercase()
        return journals.filter { journal ->
            journal.title.lowercase().contains(lowerQuery) ||
                journal.description.lowercase().contains(lowerQuery)
        }
    }
}
