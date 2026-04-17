package app.logdate.client.e2e

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import app.logdate.client.data.search.AndroidPlatformSearchIndexManager
import app.logdate.client.data.search.AndroidPlatformSearchRepository
import app.logdate.client.data.search.OfflineFirstSearchRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.search.ObserveRecentSearchesUseCase
import app.logdate.client.domain.search.UniversalSearchUseCase
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.RecentSearchesRepository
import app.logdate.client.repository.search.SearchResult
import app.logdate.feature.search.ui.SearchScreen
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState
import app.logdate.feature.search.ui.SearchViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented tests for [SearchScreenContent].
 *
 * Verifies the global search screen renders results, empty state, and the
 * real database-backed search pipeline.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: LogDateDatabase
    private lateinit var searchScope: CoroutineScope
    private lateinit var viewModel: SearchViewModel

    private val textNoteResult = SearchResult(
        uid = Uuid.random(),
        content = "Finished the final chapter of the novel today.",
        created = Clock.System.now(),
        contentType = SearchContentType.TEXT_NOTE,
    )
    private val transcriptionResult = SearchResult(
        uid = Uuid.random(),
        content = "Voice memo about the sunset hike and planning next week.",
        created = Clock.System.now(),
        contentType = SearchContentType.TRANSCRIPTION,
    )
    private val allResults = listOf(textNoteResult, transcriptionResult)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            getRoomDatabase(
                builder = Room.inMemoryDatabaseBuilder(context, LogDateDatabase::class.java),
            )

        runBlocking {
            database.textNoteDao().addNote(
                TextNoteEntity(
                    content = "Hiked the sunrise trail before breakfast.",
                    uid = Uuid.random(),
                    created = Instant.fromEpochMilliseconds(1_000),
                    lastUpdated = Instant.fromEpochMilliseconds(1_000),
                ),
            )
            database.textNoteDao().addNote(
                TextNoteEntity(
                    content = "Reviewed quarterly budget numbers at the office.",
                    uid = Uuid.random(),
                    created = Instant.fromEpochMilliseconds(2_000),
                    lastUpdated = Instant.fromEpochMilliseconds(2_000),
                ),
            )
        }

        searchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val searchRepository =
            AndroidPlatformSearchRepository(
                appSearchIndexManager =
                    AndroidPlatformSearchIndexManager(
                        context = context,
                        searchDao = database.searchDao(),
                        preferencesDataSource = LogdatePreferencesDataSource(TestPreferencesDataStore()),
                        externalScope = searchScope,
                        databaseName = "search_screen_e2e_${System.nanoTime()}",
                    ),
                roomSearchRepository =
                    OfflineFirstSearchRepository(
                        searchDao = database.searchDao(),
                        personDao = database.personDao(),
                    ),
            )
        val recentSearchesRepository = InMemoryRecentSearchesRepository()
        viewModel =
            SearchViewModel(
                universalSearchUseCase = UniversalSearchUseCase(searchRepository),
                observeRecentSearchesUseCase = ObserveRecentSearchesUseCase(recentSearchesRepository),
            )
    }

    @After
    fun tearDown() {
        searchScope.cancel()
        database.close()
    }

    @Test
    fun idleState_showsPromptText() {
        composeRule.setContent {
            SearchScreenContent(
                searchState = SearchScreenState.Idle(recentSearches = emptyList()),
                onQueryChange = {},
                onCommitSearch = {},
                onNavigateToDay = {},
                onNavigateToJournal = {},
                onNavigateToPerson = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search for entries").assertIsDisplayed()
    }

    @Test
    fun withResults_showsTextNoteContent() {
        composeRule.setContent {
            SearchScreenContent(
                searchState = SearchScreenState.Results(results = allResults),
                onQueryChange = {},
                onCommitSearch = {},
                onNavigateToDay = {},
                onNavigateToJournal = {},
                onNavigateToPerson = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finished the final chapter of the novel today.").assertIsDisplayed()
    }

    @Test
    fun withResults_showsTranscriptionContent() {
        composeRule.setContent {
            SearchScreenContent(
                searchState = SearchScreenState.Results(results = allResults),
                onQueryChange = {},
                onCommitSearch = {},
                onNavigateToDay = {},
                onNavigateToJournal = {},
                onNavigateToPerson = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "Voice memo about the sunset hike and planning next week.",
        ).assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoResultsMessage() {
        composeRule.setContent {
            SearchScreenContent(
                searchState = SearchScreenState.Empty(query = "nonexistent"),
                onQueryChange = {},
                onCommitSearch = {},
                onNavigateToDay = {},
                onNavigateToJournal = {},
                onNavigateToPerson = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("No results", substring = true).assertIsDisplayed()
    }

    @Test
    fun realSearch_typingQuery_returnsIndexedDatabaseResults() {
        composeRule.setContent {
            MaterialTheme {
                SearchScreen(
                    onNavigateToDay = {},
                    onNavigateToJournal = {},
                    onNavigateToPerson = {},
                    onGoBack = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_screen_input").performClick()
        composeRule.onNodeWithTag("search_screen_input").performTextInput("sunr")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                "Hiked the sunrise trail before breakfast.",
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule
            .onNodeWithText("Hiked the sunrise trail before breakfast.", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Reviewed quarterly budget numbers at the office.", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun realSearch_unmatchedQuery_showsEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                SearchScreen(
                    onNavigateToDay = {},
                    onNavigateToJournal = {},
                    onNavigateToPerson = {},
                    onGoBack = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_screen_input").performClick()
        composeRule.onNodeWithTag("search_screen_input").performTextInput("zzz_no_match")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                "No results for \"zzz_no_match\"",
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("No results for \"zzz_no_match\"").assertIsDisplayed()
    }
}

private class InMemoryRecentSearchesRepository : RecentSearchesRepository {
    private val recentSearches = MutableStateFlow<List<String>>(emptyList())

    override fun observeRecentSearches(): Flow<List<String>> = recentSearches

    override suspend fun addRecentSearch(query: String) {
        recentSearches.value = listOf(query) + recentSearches.value.filterNot { it == query }
    }

    override suspend fun removeRecentSearch(query: String) {
        recentSearches.value = recentSearches.value.filterNot { it == query }
    }

    override suspend fun clearRecentSearches() {
        recentSearches.value = emptyList()
    }
}

private class TestPreferencesDataStore : DataStore<Preferences> {
    private val preferencesFlow = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferencesFlow

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updatedPreferences = transform(preferencesFlow.value)
        preferencesFlow.value = updatedPreferences
        return updatedPreferences
    }
}
