package app.logdate.feature.search.ui

import app.logdate.client.domain.search.ObserveRecentSearchesUseCase
import app.logdate.client.domain.search.UniversalSearchUseCase
import app.logdate.client.repository.search.RecentSearchesRepository
import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typingShowsSearchingUntilDebouncedResultsArrive() =
        runTest(dispatcher) {
            val repository =
                FakeSearchRepository(
                    listOf(
                        SearchResult(
                            uid = Uuid.random(),
                            content = "Hiking through the redwoods",
                            created = Instant.fromEpochMilliseconds(1_000),
                            contentType = app.logdate.client.repository.search.SearchContentType.TEXT_NOTE,
                        ),
                    ),
                )
            val recentSearchesRepository = FakeRecentSearchesRepository(listOf("sunset"))
            val viewModel =
                SearchViewModel(
                    universalSearchUseCase = UniversalSearchUseCase(repository),
                    observeRecentSearchesUseCase = ObserveRecentSearchesUseCase(recentSearchesRepository),
                )
            backgroundScope.launch { viewModel.searchState.collect() }
            runCurrent()

            viewModel.updateQuery("hik")
            runCurrent()

            assertIs<SearchScreenState.Searching>(viewModel.searchState.value)

            advanceTimeBy(150)
            advanceUntilIdle()

            val resultState = assertIs<SearchScreenState.Results>(viewModel.searchState.value)
            assertEquals("hik", resultState.query)
            assertEquals(1, resultState.results.size)
        }

    private class FakeRecentSearchesRepository(
        recentSearches: List<String> = emptyList(),
    ) : RecentSearchesRepository {
        private val flow = MutableStateFlow(recentSearches)

        override fun observeRecentSearches(): Flow<List<String>> = flow

        override suspend fun addRecentSearch(query: String) {
            flow.value = listOf(query) + flow.value.filterNot { it == query }
        }

        override suspend fun removeRecentSearch(query: String) {
            flow.value = flow.value.filterNot { it == query }
        }

        override suspend fun clearRecentSearches() {
            flow.value = emptyList()
        }
    }

    private class FakeSearchRepository(
        initialResults: List<SearchResult> = emptyList(),
    ) : SearchRepository {
        private val resultsFlow = MutableStateFlow(initialResults)

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
}
