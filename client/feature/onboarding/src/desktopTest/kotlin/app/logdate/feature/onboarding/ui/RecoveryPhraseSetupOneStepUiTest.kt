package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phrase shown during setup is one step: the words with Copy and Hide, then "I’ve saved my
 * recovery phrase" or Later. It never quizzes the person on the words.
 */
@OptIn(ExperimentalTestApi::class)
class RecoveryPhraseSetupOneStepUiTest {
    private val words = List(12) { "word${it + 1}" }

    @Test
    fun savingThePhraseContinuesWithoutAQuiz() =
        runDesktopComposeUiTest(width = 720, height = 1600) {
            var saved: List<String>? = null
            setContent {
                LogDateTheme {
                    RecoveryPhraseSetupScreen(
                        words = words,
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onPhraseContinue = { saved = it },
                    )
                }
            }

            onNodeWithText("word12").assertIsDisplayed()
            onNodeWithText("Copy").assertIsDisplayed()
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onAllNodesWithText("Verify", substring = true, ignoreCase = true).assertCountEquals(0)

            onNodeWithText("I’ve saved my recovery phrase").performClick()

            assertEquals(words, saved)
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }

    @Test
    fun hidingAndLaterLeaveThePhraseOptional() =
        runDesktopComposeUiTest(width = 720, height = 1600) {
            var later = 0
            setContent {
                LogDateTheme {
                    RecoveryPhraseSetupScreen(
                        words = words,
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onPhraseContinue = {},
                        onLater = { later++ },
                    )
                }
            }

            onNodeWithText("Hide").performClick()
            onAllNodesWithText("word1").assertCountEquals(0)
            onNodeWithText("Show recovery phrase").performClick()
            onNodeWithText("word1").assertIsDisplayed()

            onNodeWithText("Later").performClick()
            assertEquals(1, later)
        }
}
