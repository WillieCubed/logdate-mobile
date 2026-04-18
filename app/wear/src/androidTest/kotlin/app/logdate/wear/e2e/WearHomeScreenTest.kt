package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.home.WearHomeContent
import app.logdate.wear.presentation.home.WearHomeUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                    homeState =
                        WearHomeUiState(
                            greeting = "Good morning",
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Good morning").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysAllCaptureChips() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = WearHomeUiState(greeting = "Good morning"),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mood Check-in").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Quick Text").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Timeline").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Navigation callbacks
    // -----------------------------------------------------------------------

    @Test
    fun homeScreen_moodCheckInChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToMoodCheckIn = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mood Check-in").performClick()
        assertTrue("Mood Check-in chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_quickTextChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToQuickText = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Quick Text").performClick()
        assertTrue("Quick Text chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_timelineChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToTimeline = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Timeline").performClick()
        assertTrue("Timeline chip should trigger navigation", navigated)
    }

    @Test
    fun homeScreen_settingsChipTriggersNavigation() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = WearHomeUiState(greeting = "Good morning"),
                    onNavigateToSettings = { navigated = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue("Settings chip should trigger navigation", navigated)
    }
}
