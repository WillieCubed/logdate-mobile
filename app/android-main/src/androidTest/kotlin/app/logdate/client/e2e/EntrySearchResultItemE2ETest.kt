package app.logdate.client.e2e

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.ui.search.EntrySearchResultItem
import app.logdate.ui.search.EntrySearchResultUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the shared [EntrySearchResultItem] composable.
 *
 * Verifies that result content, date, type label, and click
 * callbacks all render and behave correctly.
 */
@RunWith(AndroidJUnit4::class)
class EntrySearchResultItemE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    private val textNoteState = EntrySearchResultUiState(
        id = "result-1",
        contentText = AnnotatedString("Watched the sunset from the balcony."),
        dateLabel = "Mar 15, 2026",
        typeLabel = "Text note",
        typeIcon = Icons.Default.Search,
    )

    private val voiceNoteState = EntrySearchResultUiState(
        id = "result-2",
        contentText = AnnotatedString("Quick memo about tomorrow's schedule."),
        dateLabel = "Mar 16, 2026",
        typeLabel = "Voice note",
        typeIcon = Icons.Default.Mic,
    )

    // region Rendering

    @Test
    fun textNote_displaysContent() {
        composeRule.setContent {
            EntrySearchResultItem(
                state = textNoteState,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Watched the sunset from the balcony.").assertIsDisplayed()
    }

    @Test
    fun textNote_displaysDateAndTypeLabel() {
        composeRule.setContent {
            EntrySearchResultItem(
                state = textNoteState,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Mar 15, 2026 · Text note").assertIsDisplayed()
    }

    @Test
    fun voiceNote_displaysContent() {
        composeRule.setContent {
            EntrySearchResultItem(
                state = voiceNoteState,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Quick memo about tomorrow's schedule.").assertIsDisplayed()
    }

    @Test
    fun voiceNote_displaysDateAndTypeLabel() {
        composeRule.setContent {
            EntrySearchResultItem(
                state = voiceNoteState,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Mar 16, 2026 · Voice note").assertIsDisplayed()
    }

    // endregion

    // region Interaction

    @Test
    fun click_invokesCallback() {
        var clicked = false

        composeRule.setContent {
            EntrySearchResultItem(
                state = textNoteState,
                onClick = { clicked = true },
            )
        }

        composeRule.onNodeWithText("Watched the sunset from the balcony.").performClick()

        assert(clicked) { "Expected onClick to be called" }
    }

    // endregion
}
