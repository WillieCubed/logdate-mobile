package app.logdate.client.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import app.logdate.feature.search.ui.SearchScreenContent
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Instrumented tests for [SearchScreenContent].
 *
 * Verifies the global search screen renders results, empty state,
 * and handles navigation callbacks correctly.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    private val textNoteResult = SearchResult(
        uid = Uuid.random(),
        content = "Finished the final chapter of the novel today.",
        created = Clock.System.now(),
        type = SearchResultType.TEXT_NOTE,
    )
    private val transcriptionResult = SearchResult(
        uid = Uuid.random(),
        content = "Voice memo about the sunset hike and planning next week.",
        created = Clock.System.now(),
        type = SearchResultType.TRANSCRIPTION,
    )
    private val allResults = listOf(textNoteResult, transcriptionResult)

    // region Empty State

    @Test
    fun emptyState_showsPromptText() {
        composeRule.setContent {
            SearchScreenContent(
                searchResults = emptyList(),
                onQueryChange = {},
                onNavigateToDay = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search for entries").assertIsDisplayed()
    }

    // endregion

    // region Results Display

    @Test
    fun withResults_showsTextNoteContent() {
        composeRule.setContent {
            SearchScreenContent(
                searchResults = allResults,
                onQueryChange = {},
                onNavigateToDay = {},
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
                searchResults = allResults,
                onQueryChange = {},
                onNavigateToDay = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "Voice memo about the sunset hike and planning next week.",
        ).assertIsDisplayed()
    }

    @Test
    fun withResults_showsTypeLabels() {
        composeRule.setContent {
            SearchScreenContent(
                searchResults = allResults,
                onQueryChange = {},
                onNavigateToDay = {},
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Text note", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Voice note", substring = true).assertIsDisplayed()
    }

    // endregion

    // region Navigation Callbacks

    @Test
    fun tappingResult_invokesOnNavigateToDay() {
        var navigatedToDay: LocalDate? = null

        composeRule.setContent {
            SearchScreenContent(
                searchResults = listOf(textNoteResult),
                onQueryChange = {},
                onNavigateToDay = { navigatedToDay = it },
                onGoBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finished the final chapter of the novel today.").performClick()
        composeRule.waitForIdle()

        assert(navigatedToDay != null) {
            "Expected onNavigateToDay to be called"
        }
    }

    @Test
    fun backArrow_invokesOnGoBack() {
        var wentBack = false

        composeRule.setContent {
            SearchScreenContent(
                searchResults = emptyList(),
                onQueryChange = {},
                onNavigateToDay = {},
                onGoBack = { wentBack = true },
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Go back").performClick()
        composeRule.waitForIdle()

        assert(wentBack) { "Expected onGoBack to be called" }
    }

    // endregion
}
