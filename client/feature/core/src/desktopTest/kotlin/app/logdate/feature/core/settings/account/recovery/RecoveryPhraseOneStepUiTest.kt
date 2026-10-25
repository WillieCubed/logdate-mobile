package app.logdate.feature.core.settings.account.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * Account > Recovery phrase walked as the person does: Show, the words with Copy and Hide, and "I’ve
 * saved my recovery phrase". Nothing on the way asks them to type the words back.
 */
@OptIn(ExperimentalTestApi::class)
class RecoveryPhraseOneStepUiTest {
    private val words = List(12) { "word${it + 1}" }

    @Test
    fun showingAndSavingThePhraseNeverAsksForTheWords() =
        runDesktopComposeUiTest(width = 720, height = 1600) {
            var state by mutableStateOf<RecoveryPhraseUiState>(RecoveryPhraseUiState.Hidden)
            setContent {
                LogDateTheme {
                    RecoveryPhraseContent(
                        state = state,
                        onBack = {},
                        onReveal = { state = RecoveryPhraseUiState.Revealed(words) },
                        onHide = { state = RecoveryPhraseUiState.Hidden },
                        onEnterPhrase = {},
                        onConfirmSaved = { state = RecoveryPhraseUiState.Verified },
                    )
                }
            }

            onNodeWithText("Show recovery phrase").assertIsDisplayed().performClick()
            onNodeWithText("word1").assertIsDisplayed()
            onNodeWithText("Copy").assertIsDisplayed()
            onNodeWithText("Hide").assertIsDisplayed()
            onAllNodes(hasSetTextAction()).assertCountEquals(0)

            onNode(hasScrollAction()).performScrollToNode(hasText("I’ve saved my recovery phrase"))
            onNodeWithText("I’ve saved my recovery phrase").performClick()

            onNodeWithText("You’ve saved a copy of your recovery phrase.").assertIsDisplayed()
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onAllNodesWithText("Verify", substring = true, ignoreCase = true).assertCountEquals(0)
        }

    @Test
    fun accountRecoveryThatNeedsAPasskeyIsShownBeforeAnyCopyIsSaved() =
        runDesktopComposeUiTest(width = 720, height = 1600) {
            var finishes = 0
            setContent {
                LogDateTheme {
                    RecoveryPhraseContent(
                        state = RecoveryPhraseUiState.Hidden,
                        onBack = {},
                        onReveal = {},
                        onHide = {},
                        onEnterPhrase = {},
                        accountRecovery = AccountRecoveryUiState.NeedsSetup,
                        onFinishAccountRecovery = { finishes++ },
                    )
                }
            }

            onNodeWithText("Finish setting up").assertIsDisplayed().performClick()
            kotlin.test.assertEquals(1, finishes)
        }
}
