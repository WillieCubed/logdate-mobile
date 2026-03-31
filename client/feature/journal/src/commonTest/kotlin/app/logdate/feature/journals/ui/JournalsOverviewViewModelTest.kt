package app.logdate.feature.journals.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.search.SearchEntriesUseCase
import app.logdate.client.domain.search.SearchJournalsUseCase
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class JournalsOverviewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val tripJournal =
        Journal(
            id = Uuid.random(),
            title = "Summer Trip 2025",
            description = "Our family vacation to the coast",
        )
    private val workJournal =
        Journal(
            id = Uuid.random(),
            title = "Work Notes",
            description = "Daily standup summaries and project ideas",
        )

    private val journalRepository = TestJournalRepository(listOf(tripJournal, workJournal))
    private val preferencesDataSource = LogdatePreferencesDataSource(TestPreferencesDataStore())
    private val searchJournalsUseCase = SearchJournalsUseCase(journalRepository)
    private val searchRepository = TestSearchRepository()
    private val searchEntriesUseCase = SearchEntriesUseCase(searchRepository)

    private lateinit var viewModel: JournalsOverviewViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            JournalsOverviewViewModel(
                repository = journalRepository,
                preferencesDataSource = preferencesDataSource,
                searchJournalsUseCase = searchJournalsUseCase,
                searchEntriesUseCase = searchEntriesUseCase,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty search query`() =
        runTest {
            startUiStateCollection()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals("", state.searchQuery)
        }

    @Test
    fun `initial state includes all journals plus placeholder`() =
        runTest {
            startUiStateCollection()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            // 2 journals + 1 CreateJournalPlaceholder
            assertEquals(3, state.journals.size)
            assertTrue(state.journals.last() is JournalListItemUiState.CreateJournalPlaceholder)
        }

    @Test
    fun `updateSearchQuery filters journals by title`() =
        runTest {
            startUiStateCollection()
            viewModel.updateSearchQuery("trip")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("trip", state.searchQuery)

            val existingJournals = state.journals.filterIsInstance<JournalListItemUiState.ExistingJournal>()
            assertEquals(1, existingJournals.size)
            assertEquals("Summer Trip 2025", existingJournals[0].data.title)
        }

    @Test
    fun `updateSearchQuery filters journals by description`() =
        runTest {
            startUiStateCollection()
            viewModel.updateSearchQuery("standup")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            val existingJournals = state.journals.filterIsInstance<JournalListItemUiState.ExistingJournal>()
            assertEquals(1, existingJournals.size)
            assertEquals("Work Notes", existingJournals[0].data.title)
        }

    @Test
    fun `clearing query restores all journals`() =
        runTest {
            startUiStateCollection()
            viewModel.updateSearchQuery("trip")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSearchQuery("")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            val existingJournals = state.journals.filterIsInstance<JournalListItemUiState.ExistingJournal>()
            assertEquals(2, existingJournals.size)
        }

    @Test
    fun `placeholder always present even with active search`() =
        runTest {
            startUiStateCollection()
            viewModel.updateSearchQuery("trip")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.journals.any { it is JournalListItemUiState.CreateJournalPlaceholder })
        }

    @Test
    fun `single character query still requests entry preview search`() =
        runTest {
            startUiStateCollection()
            viewModel.updateSearchQuery("t")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("t" to 5), searchRepository.limitedQueries)
        }

    // region Fakes

    private fun TestScope.startUiStateCollection() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private class TestJournalRepository(
        initialJournals: List<Journal>,
    ) : JournalRepository {
        private val journalsFlow = MutableStateFlow(initialJournals)

        override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

        override fun observeJournalById(id: Uuid): Flow<Journal> = throw NotImplementedError()

        override suspend fun getJournalById(id: Uuid): Journal? = journalsFlow.value.find { it.id == id }

        override suspend fun create(journal: Journal): Uuid {
            journalsFlow.value = journalsFlow.value + journal
            return journal.id
        }

        override suspend fun update(journal: Journal) {}

        override suspend fun delete(journalId: Uuid) {}

        override suspend fun saveDraft(draft: EditorDraft) {}

        override suspend fun getLatestDraft(): EditorDraft? = null

        override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = null

        override suspend fun deleteDraft(id: Uuid) {}
    }

    private class TestSearchRepository : SearchRepository {
        val limitedQueries = mutableListOf<Pair<String, Int>>()

        override fun search(query: String): Flow<List<SearchResult>> = flowOf(emptyList())

        override fun searchWithLimit(
            query: String,
            limit: Int,
        ): Flow<List<SearchResult>> {
            limitedQueries += query to limit
            return flowOf(emptyList())
        }

        override fun searchWithSnippets(query: String): Flow<List<SearchResult>> = flowOf(emptyList())

        override fun searchRanked(
            query: String,
            limit: Int,
        ): Flow<List<SearchResult>> = flowOf(emptyList())
    }

    private class TestPreferencesDataStore : DataStore<Preferences> {
        private val prefsFlow = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = prefsFlow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val newPrefs = transform(prefsFlow.value)
            prefsFlow.value = newPrefs
            return newPrefs
        }
    }

    // endregion
}
