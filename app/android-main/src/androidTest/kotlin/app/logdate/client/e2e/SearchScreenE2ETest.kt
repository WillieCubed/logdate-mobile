package app.logdate.client.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Instrumented tests for [SearchScreenContent].
 *
 * Verifies the global search screen renders results and empty state correctly.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun idleState_showsPromptText() {
        composeRule.setContent {
            SearchScreenContent(
                searchState = SearchScreenState.Idle(recentSearches = emptyList()),
                onQueryChange = {},
                onCommitSearch = {},
                onNavigateToDay = {},
                onNavigateToJournal = {},
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
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("No results", substring = true).assertIsDisplayed()
    }
}
