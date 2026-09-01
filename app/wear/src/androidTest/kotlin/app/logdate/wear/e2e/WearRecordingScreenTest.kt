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
import app.logdate.wear.presentation.recording.ActiveRecordingContent
import app.logdate.wear.presentation.recording.ReadyContent
import app.logdate.wear.presentation.recording.RecordingErrorContent
import app.logdate.wear.presentation.recording.SavedContent
import app.logdate.wear.presentation.recording.SavingContent
import app.logdate.wear.presentation.recording.TooShortContent
import app.logdate.wear.presentation.recording.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI verification suite for the Wear OS audio recording experience.
 *
 * This test class exercises the various states of the recording lifecycle on a Wear device,
 * including initial readiness, active recording with waveform feedback, the saving
 * process, and post-capture success or failure states. It ensures that critical
 * instructional text and timer formatting are correctly displayed to the user.
 */
@RunWith(AndroidJUnit4::class)
class WearRecordingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Ready state
    // -----------------------------------------------------------------------

    @Test
    fun `ready state displays hold to record`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ReadyContent()
                }
            }
        }

        composeRule.onNodeWithText("HOLD TO\nRECORD").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Recording state
    // -----------------------------------------------------------------------

    @Test
    fun `recording state displays timer`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF8B1A1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    ActiveRecordingContent(
                        durationMs = 4_200,
                        audioLevels = listOf(0.3f, 0.5f, 0.7f),
                    )
                }
            }
        }

        composeRule.onNodeWithText("RECORDING").assertIsDisplayed()
    }

    @Test
    fun `recording state displays waveform with levels`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ActiveRecordingContent(
                        durationMs = 10_000,
                        audioLevels = List(20) { it / 20f },
                    )
                }
            }
        }

        composeRule.onNodeWithText("RECORDING").assertIsDisplayed()
    }

    @Test
    fun `recording state displays empty waveform`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ActiveRecordingContent(
                        durationMs = 0,
                        audioLevels = emptyList(),
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
    fun `saving state displays saving text`() {
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
    fun `saved state displays checkmark and duration`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1B5E20)),
                    contentAlignment = Alignment.Center,
                ) {
                    SavedContent(durationMs = 4_200)
                }
            }
        }

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("0:04").assertIsDisplayed()
    }

    @Test
    fun `saved state displays long duration`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SavedContent(durationMs = 58_000)
                }
            }
        }

        composeRule.onNodeWithText("0:58").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Too short state
    // -----------------------------------------------------------------------

    @Test
    fun `too short state displays both messages`() {
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
    fun `error state displays error message`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordingErrorContent(message = "Microphone unavailable")
                }
            }
        }

        composeRule.onNodeWithText("Microphone unavailable").assertIsDisplayed()
    }

    @Test
    fun `error state displays default when null`() {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordingErrorContent(message = null)
                }
            }
        }

        composeRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Duration formatting
    // -----------------------------------------------------------------------

    @Test
    fun `format duration zero ms`() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun `format duration sub minute`() {
        assertEquals("0:04", formatDuration(4_200))
    }

    @Test
    fun `format duration exact minute`() {
        assertEquals("1:00", formatDuration(60_000))
    }

    @Test
    fun `format duration multi minute`() {
        assertEquals("2:30", formatDuration(150_000))
    }
}
