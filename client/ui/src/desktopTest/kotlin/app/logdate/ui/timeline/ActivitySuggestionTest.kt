@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's timeline offers a moment after a walk or trip, and a morning start that records a voice note. */
@OptIn(ExperimentalTestApi::class)
class ActivitySuggestionTest {
    @Test
    fun `a finished walk names its length and adds a moment`() =
        runDesktopComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                Pane(
                    TimelineSuggestionBlock.AfterActivity(minutes = 40, trip = false),
                    onUse = { calls += "use" },
                    onStartWriting = { calls += "write" },
                )
            }

            onNodeWithText("Activity finished").assertExists()
            onNodeWithText("You were moving for 40 minutes. How did it go?").assertExists()
            onNodeWithTag(TimelineSuggestionTags.ADD_MOMENT).performClick()

            assertEquals(listOf("use", "write"), calls)
        }

    @Test
    fun `a finished trip asks where it went`() =
        runDesktopComposeUiTest {
            setContent { Pane(TimelineSuggestionBlock.AfterActivity(minutes = 25, trip = true)) }

            onNodeWithText("Trip finished").assertExists()
            onNodeWithText("Your trip took 25 minutes. Where did you go?").assertExists()
        }

    @Test
    fun `the morning start records a voice note`() =
        runDesktopComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                Pane(
                    TimelineSuggestionBlock.MorningStart,
                    onUse = { calls += "use" },
                    onStartWriting = { calls += "write" },
                    onRecordVoice = { calls += "voice" },
                )
            }

            onNodeWithTag(TimelineSuggestionTags.ADD_MOMENT).assertDoesNotExist()
            onNodeWithText("Record a voice note").performClick()

            assertEquals(listOf("use", "voice"), calls)
        }

    @Test
    fun `the morning start can be dismissed`() =
        runDesktopComposeUiTest {
            var dismissed = 0
            setContent { Pane(TimelineSuggestionBlock.MorningStart, onDismiss = { dismissed++ }) }

            onNodeWithText("Good morning").assertExists()
            onNodeWithText("How is your day starting?").assertExists()
            onNodeWithTag(TimelineSuggestionTags.DISMISS).performClick()

            assertEquals(1, dismissed)
        }

    @Test
    fun `other suggestions keep their own actions`() =
        runDesktopComposeUiTest {
            setContent { Pane(TimelineSuggestionBlock.EmptyDay(message = "Nothing yet today")) }

            onNodeWithTag(TimelineSuggestionTags.ADD_MOMENT).assertDoesNotExist()
            onNodeWithTag(TimelineSuggestionTags.DISMISS).assertDoesNotExist()
        }
}

@Composable
private fun Pane(
    suggestion: TimelineSuggestionBlock,
    onUse: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onStartWriting: () -> Unit = {},
    onRecordVoice: () -> Unit = {},
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        TimelinePane(
            uiState = TimelineUiState(),
            onNewEntry = {},
            onOpenDay = {},
            timelineSuggestion = suggestion,
            onStartWriting = onStartWriting,
            onDismissSuggestion = onDismiss,
            onUseSuggestion = onUse,
            onRecordVoice = onRecordVoice,
        )
    }
}
