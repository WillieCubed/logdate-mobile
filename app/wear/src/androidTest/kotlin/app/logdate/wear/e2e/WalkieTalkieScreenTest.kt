package app.logdate.wear.e2e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.walkietalkie.ReadyContent
import app.logdate.wear.presentation.walkietalkie.RecordingContent
import app.logdate.wear.presentation.walkietalkie.SavingContent
import app.logdate.wear.presentation.walkietalkie.TooShortContent
import app.logdate.wear.presentation.walkietalkie.WalkieTalkieErrorContent
import app.logdate.wear.presentation.walkietalkie.WalkieTalkieSavedContent
import app.logdate.wear.presentation.walkietalkie.formatDuration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class WalkieTalkieScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Ready state
    // -----------------------------------------------------------------------

    @Test
    fun readyState_displaysHoldToRecord() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ReadyContent(onTouchDown = {}, onTouchUp = {})
                }
            }
        }

        composeRule.onNodeWithText("HOLD TO\nRECORD").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Recording state
    // -----------------------------------------------------------------------

    @Test
    fun recordingState_displaysTimer() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8B1A1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordingContent(
                        durationMs = 4_200,
                        audioLevels = listOf(0.3f, 0.5f, 0.7f),
                        onTouchUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("RECORDING").assertIsDisplayed()
    }

    @Test
    fun recordingState_displaysWaveformWithLevels() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordingContent(
                        durationMs = 10_000,
                        audioLevels = List(20) { it / 20f },
                        onTouchUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("RECORDING").assertIsDisplayed()
    }

    @Test
    fun recordingState_displaysEmptyWaveform() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordingContent(
                        durationMs = 0,
                        audioLevels = emptyList(),
                        onTouchUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("RECORDING").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Saving state
    // -----------------------------------------------------------------------

    @Test
    fun savingState_displaysSavingText() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SavingContent()
                }
            }
        }

        composeRule.onNodeWithText("Saving...").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Saved state
    // -----------------------------------------------------------------------

    @Test
    fun savedState_displaysCheckmarkAndDuration() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B5E20)),
                    contentAlignment = Alignment.Center,
                ) {
                    WalkieTalkieSavedContent(durationMs = 4_200)
                }
            }
        }

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("0:04").assertIsDisplayed()
    }

    @Test
    fun savedState_displaysLongDuration() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    WalkieTalkieSavedContent(durationMs = 58_000)
                }
            }
        }

        composeRule.onNodeWithText("0:58").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Too short state
    // -----------------------------------------------------------------------

    @Test
    fun tooShortState_displaysBothMessages() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TooShortContent()
                }
            }
        }

        composeRule.onNodeWithText("Too short").assertIsDisplayed()
        composeRule.onNodeWithText("Hold longer").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Error state
    // -----------------------------------------------------------------------

    @Test
    fun errorState_displaysErrorMessage() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    WalkieTalkieErrorContent(message = "Microphone unavailable")
                }
            }
        }

        composeRule.onNodeWithText("Microphone unavailable").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysDefaultWhenNull() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    WalkieTalkieErrorContent(message = null)
                }
            }
        }

        composeRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Duration formatting
    // -----------------------------------------------------------------------

    @Test
    fun formatDuration_zeroMs() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun formatDuration_subMinute() {
        assertEquals("0:04", formatDuration(4_200))
    }

    @Test
    fun formatDuration_exactMinute() {
        assertEquals("1:00", formatDuration(60_000))
    }

    @Test
    fun formatDuration_multiMinute() {
        assertEquals("2:30", formatDuration(150_000))
    }
}
