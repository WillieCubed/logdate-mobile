package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.home.WearHomeContent
import app.logdate.wear.presentation.home.WearHomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class WearHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Content rendering
    // -----------------------------------------------------------------------

    @Test
    fun homeScreen_displaysGreeting() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(
                        greeting = "Good morning",
                        entryCount = 0,
                        entryCountLabel = "No entries yet",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Good morning").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysEntryCount() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(
                        greeting = "Good afternoon",
                        entryCount = 3,
                        entryCountLabel = "3 entries today",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("3 entries today").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysNoEntriesLabel() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(
                        greeting = "Good evening",
                        entryCount = 0,
                        entryCountLabel = "No entries yet",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("No entries yet").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysSingleEntryLabel() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(
                        greeting = "Good morning",
                        entryCount = 1,
                        entryCountLabel = "1 entry today",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("1 entry today").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysAllCaptureChips() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                )
            }
        }

        composeRule.onNodeWithText("Walkie-Talkie").assertIsDisplayed()
        composeRule.onNodeWithText("Voice Note").assertIsDisplayed()
        composeRule.onNodeWithText("Mood Check-in").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Text").assertIsDisplayed()
        composeRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Navigation callbacks
    // -----------------------------------------------------------------------

    @Test
    fun homeScreen_walkieTalkieChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToWalkieTalkie = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Walkie-Talkie").performClick()
        assertTrue("Walkie-Talkie chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_voiceNoteChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToVoiceNote = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Voice Note").performClick()
        assertTrue("Voice Note chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_moodCheckInChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToMoodCheckIn = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Mood Check-in").performClick()
        assertTrue("Mood Check-in chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_quickTextChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToQuickText = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Quick Text").performClick()
        assertTrue("Quick Text chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_timelineChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToTimeline = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Timeline").performClick()
        assertTrue("Timeline chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_settingsChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    uiState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToSettings = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithText("Settings").performClick()
        assertTrue("Settings chip should trigger navigation", navigated)
    }
}
