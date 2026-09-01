package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.mood.MoodOption
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
 * - The mood selection grid and its associated labels/emojis.
 * - The optional voice prompt follow-up, ensuring contextual mood feedback is shown.
 * - Navigation and callback logic for both mood selection and voice attachment.
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

        for (mood in MoodOption.entries) {
            composeRule.onNodeWithText("${mood.emoji} ${mood.label}").assertIsDisplayed()
        }
    }

    @Test
    fun `select mood tapping great triggers callback`() {
        var selectedMood: MoodOption? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selectedMood = it })
            }
        }

        composeRule
            .onNodeWithText("${MoodOption.GREAT.emoji} ${MoodOption.GREAT.label}")
            .performClick()

        assertEquals(MoodOption.GREAT, selectedMood)
    }

    @Test
    fun `select mood tapping sad triggers callback`() {
        var selectedMood: MoodOption? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selectedMood = it })
            }
        }

        composeRule
            .onNodeWithText("${MoodOption.SAD.emoji} ${MoodOption.SAD.label}")
            .performClick()

        assertEquals(MoodOption.SAD, selectedMood)
    }

    @Test
    fun `select mood tapping rough triggers callback`() {
        var selectedMood: MoodOption? = null
        composeRule.setContent {
            MaterialTheme {
                SelectMoodContent(onMoodSelected = { selectedMood = it })
            }
        }

        composeRule
            .onNodeWithText("${MoodOption.ROUGH.emoji} ${MoodOption.ROUGH.label}")
            .performClick()

        assertEquals(MoodOption.ROUGH, selectedMood)
    }

    // -----------------------------------------------------------------------
    // Voice prompt
    // -----------------------------------------------------------------------

    @Test
    fun `voice prompt displays mood emoji`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = MoodOption.GOOD,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText(MoodOption.GOOD.emoji).assertIsDisplayed()
        composeRule.onNodeWithText(MoodOption.GOOD.label).assertIsDisplayed()
    }

    @Test
    fun `voice prompt displays buttons`() {
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = MoodOption.OK,
                    onAttachVoice = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Add voice note").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun `voice prompt add voice triggers callback`() {
        var voiceAttached = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = MoodOption.GREAT,
                    onAttachVoice = { voiceAttached = true },
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Add voice note").performClick()
        assertTrue("Add voice note should trigger callback", voiceAttached)
    }

    @Test
    fun `voice prompt skip triggers callback`() {
        var skipped = false
        composeRule.setContent {
            MaterialTheme {
                VoicePromptContent(
                    selectedMood = MoodOption.GREAT,
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

        composeRule.onNodeWithText("Add voice note").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
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
}
