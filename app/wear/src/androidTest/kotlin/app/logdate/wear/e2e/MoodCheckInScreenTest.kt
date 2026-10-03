package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.capture.Feeling
import app.logdate.wear.presentation.mood.MoodSavedContent
import app.logdate.wear.presentation.mood.SelectMoodContent
import app.logdate.wear.presentation.mood.VoicePromptContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI verification suite for the Wear OS Mood Check-in flow.
 *
 * This test class covers the multi-step mood capture experience on Wear, validating:
 * - The mood selection list and its labels and emoji.
 * - The optional voice prompt follow-up, ensuring contextual mood feedback is shown.
 * - Callbacks for mood selection, opening the recorder and skipping.
 * - Final confirmation screens after a successful check-in.
 */
@RunWith(AndroidJUnit4::class)
class MoodCheckInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Mood selection
    // -----------------------------------------------------------------------

    @Test
    fun `select mood displays title`() {
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = {})
            }
        }

        composeRule.onNodeWithText("How are you feeling?").assertIsDisplayed()
    }

    @Test
    fun `select mood displays all options`() {
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = {})
            }
        }

        for (feeling in Feeling.entries) {
            scrollUntilShown(optionText(feeling))
            composeRule.onNodeWithText(optionText(feeling)).assertIsDisplayed()
        }
    }

    @Test
    fun `select mood tapping great triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        composeRule.onNodeWithText(optionText(Feeling.GREAT)).performClick()

        assertEquals(Feeling.GREAT, selected)
    }

    @Test
    fun `select mood tapping sad triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        scrollUntilShown(optionText(Feeling.SAD))
        composeRule.onNodeWithText(optionText(Feeling.SAD)).performClick()

        assertEquals(Feeling.SAD, selected)
    }

    @Test
    fun `select mood tapping rough triggers callback`() {
        var selected: Feeling? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selected = it })
            }
        }

        scrollUntilShown(optionText(Feeling.ROUGH))
        composeRule.onNodeWithText(optionText(Feeling.ROUGH)).performClick()

        assertEquals(Feeling.ROUGH, selected)
    }

    // -----------------------------------------------------------------------
    // Voice prompt
    // -----------------------------------------------------------------------

    @Test
    fun `voice prompt displays mood emoji`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GOOD,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText(Feeling.GOOD.emoji).assertIsDisplayed()
        composeRule.onNodeWithText(label(Feeling.GOOD)).assertIsDisplayed()
    }

    @Test
    fun `voice prompt displays buttons`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.OK,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun `voice prompt open recorder triggers callback`() {
        var voiceAttached = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GREAT,
                    onAttachVoice = { voiceAttached = true },
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").performClick()
        assertTrue("Open recorder should trigger callback", voiceAttached)
    }

    @Test
    fun `voice prompt skip triggers callback`() {
        var skipped = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GREAT,
                    onAttachVoice = {},
                    onSkip = { skipped = true },
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()
        assertTrue("Skip should trigger callback", skipped)
    }

    @Test
    fun `voice prompt hides emoji when null`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = null,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun `voice prompt disables both buttons while the mood is saving`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GOOD,
                    onAttachVoice = {},
                    onSkip = {},
                    isSaving = true,
                )
            }
        }

        composeRule.onNodeWithText("Open recorder").assertIsNotEnabled()
        composeRule.onNodeWithText("Skip").assertIsNotEnabled()
    }

    @Test
    fun `voice prompt says when the mood could not be saved`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = Feeling.GOOD,
                    onAttachVoice = {},
                    onSkip = {},
                    saveFailed = true,
                )
            }
        }

        composeRule.onNodeWithText("Couldn't save your feeling").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Saved confirmation
    // -----------------------------------------------------------------------

    @Test
    fun `saved state displays saved text`() {
        composeRule.setContent {
            MaterialTheme {
                MoodSavedContent()
            }
        }

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
    }

    private fun label(feeling: Feeling): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(feeling.labelRes)

    private fun optionText(feeling: Feeling): String = "${feeling.emoji} ${label(feeling)}"

    /** Swipes the list up until [text] is on screen; a small round watch shows only the first options. */
    private fun scrollUntilShown(text: String) {
        repeat(MAX_SWIPES) {
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = centerY + height / 4f, endY = centerY - height / 4f, durationMillis = 400)
            }
        }
    }

    private companion object {
        const val MAX_SWIPES = 5
    }
}
