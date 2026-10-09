package app.logdate.wear.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.home.WearHomeContent
import app.logdate.wear.presentation.home.WearHomeUiState
import app.logdate.wear.presentation.recording.RecordingError
import app.logdate.wear.presentation.recording.RecordingPhase
import app.logdate.wear.presentation.recording.RecordingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * UI tests for the Wear OS Home Screen, which is the voice recorder.
 *
 * These verify that each recorder state shows the controls that make sense for it (Memories, Mood
 * and More when idle; Pause and Discard beside a latched recording; Undo after a save; Allow after a
 * denied microphone) and that each control calls back. The record surface exposes a single click
 * action for assistive services, which behaves like a tap.
 */
@RunWith(AndroidJUnit4::class)
class WearHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val home = WearHomeUiState(greeting = "Good morning")

    // -----------------------------------------------------------------------
    // Idle content
    // -----------------------------------------------------------------------

    @Test
    fun `home screen displays greeting`() {
        composeRule.setContent { MaterialTheme { WearHomeContent(homeState = home) } }

        composeRule.onNodeWithText("Good morning").assertIsDisplayed()
    }

    @Test
    fun `idle home screen shows the recorder and its neighbours`() {
        composeRule.setContent { MaterialTheme { WearHomeContent(homeState = home) } }

        composeRule.onNodeWithContentDescription("Record Audio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Voice memories").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mood Check-in").assertIsDisplayed()
        composeRule.onNodeWithText("More").assertIsDisplayed()
    }

    @Test
    fun `the gesture hint replaces the greeting until it has been seen`() {
        composeRule.setContent {
            MaterialTheme { WearHomeContent(homeState = home, recordingState = RecordingUiState(showGestureHint = true)) }
        }

        composeRule.onNodeWithText("Tap to record\nor hold to talk").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Navigation callbacks
    // -----------------------------------------------------------------------

    @Test
    fun `memories button triggers navigation`() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme { WearHomeContent(homeState = home, onNavigateToMemories = { navigated = true }) }
        }

        composeRule.onNodeWithContentDescription("Voice memories").performClick()

        assertTrue("Voice memories should trigger navigation", navigated)
    }

    @Test
    fun `mood check in button triggers navigation`() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme { WearHomeContent(homeState = home, onNavigateToMoodCheckIn = { navigated = true }) }
        }

        composeRule.onNodeWithContentDescription("Mood Check-in").performClick()

        assertTrue("Mood Check-in should trigger navigation", navigated)
    }

    @Test
    fun `more button triggers navigation`() {
        var navigated = false
        composeRule.setContent {
            MaterialTheme { WearHomeContent(homeState = home, onNavigateToMore = { navigated = true }) }
        }

        composeRule.onNodeWithText("More").performClick()

        assertTrue("More should trigger navigation", navigated)
    }

    // -----------------------------------------------------------------------
    // Recorder
    // -----------------------------------------------------------------------

    @Test
    fun `assistive click on the record surface presses and releases once`() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    onPress = { events += "press" },
                    onRelease = { events += "release" },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Record Audio").performClick()

        assertEquals(listOf("press", "release"), events)
    }

    @Test
    fun `a hold keeps going when the finger slides off the button and ends only when it lifts`() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    onPress = { events += "press" },
                    onRelease = { events += "release" },
                )
            }
        }
        val button = composeRule.onNodeWithContentDescription("Record Audio")

        button.performTouchInput { down(center) }
        assertEquals(listOf("press"), events)

        button.performTouchInput { moveTo(center + Offset(width * 3f, 0f)) }
        assertEquals("Sliding off the button must not end the hold", listOf("press"), events)

        button.performTouchInput { up() }
        assertEquals(listOf("press", "release"), events)
    }

    @Test
    fun `a latched recording shows pause and discard and hides the idle shortcuts`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(
                            phase = RecordingPhase.RECORDING,
                            isLatched = true,
                            recordingDurationMs = 12_000,
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Discard recording").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop recording").assertIsDisplayed()
        composeRule.onNodeWithText("0:12").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Voice memories")).assertCountEquals(0)
    }

    @Test
    fun `pause and discard call back`() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState = RecordingUiState(phase = RecordingPhase.RECORDING, isLatched = true),
                    onPauseToggle = { events += "pause" },
                    onDiscard = { events += "discard" },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Discard recording").performClick()

        assertEquals(listOf("pause", "discard"), events)
    }

    @Test
    fun `a discard question keeps the timer and replaces the waveform with the question`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(
                            phase = RecordingPhase.RECORDING,
                            isLatched = true,
                            recordingDurationMs = 84_000,
                            confirmingDiscard = true,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("1:24").assertIsDisplayed()
        composeRule.onNodeWithText("Tap again to discard").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tap again to discard").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Discard recording")).assertCountEquals(0)
    }

    @Test
    fun `a discard question is announced to screen readers`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(phase = RecordingPhase.RECORDING, isLatched = true, confirmingDiscard = true),
                )
            }
        }

        composeRule
            .onNodeWithText("Tap again to discard")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `a paused recording being discarded keeps the timer and shows the question`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(
                            phase = RecordingPhase.PAUSED,
                            isLatched = true,
                            recordingDurationMs = 12_000,
                            confirmingDiscard = true,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("0:12").assertIsDisplayed()
        composeRule.onNodeWithText("Tap again to discard").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Paused")).assertCountEquals(0)
    }

    @Test
    fun `the confirming control calls back to discard`() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(phase = RecordingPhase.RECORDING, isLatched = true, confirmingDiscard = true),
                    onDiscard = { events += "discard" },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Tap again to discard").performClick()

        assertEquals(listOf("discard"), events)
    }

    @Test
    fun `a full watch offers a way to its storage settings`() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.NOT_ENOUGH_STORAGE),
                    onOpenStorageSettings = { events += "storage" },
                )
            }
        }

        composeRule.onNodeWithText("Storage").performClick()

        assertEquals(listOf("storage"), events)
    }

    @Test
    fun `a paused recording offers resume and says who paused it`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(
                            phase = RecordingPhase.PAUSED,
                            isLatched = true,
                            pausedByInterruption = true,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Paused by another app").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
    }

    @Test
    fun `a saved recording offers undo`() {
        var undone = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState = RecordingUiState(phase = RecordingPhase.SAVED, undoableNoteId = Uuid.random()),
                    onUndo = { undone = true },
                )
            }
        }

        composeRule.onNodeWithText("Undo").performClick()

        assertTrue("Undo should call back", undone)
    }

    @Test
    fun `a denied microphone explains itself and offers allow`() {
        var allowed = false
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState =
                        RecordingUiState(
                            phase = RecordingPhase.ERROR,
                            error = RecordingError.MICROPHONE_PERMISSION_DENIED,
                        ),
                    onAllowMicrophone = { allowed = true },
                )
            }
        }

        composeRule.onNodeWithText("Microphone is off").assertIsDisplayed()
        composeRule.onNodeWithText("Allow").performClick()

        assertTrue("Allow should call back", allowed)
    }

    @Test
    fun `a full watch says so`() {
        composeRule.setContent {
            MaterialTheme {
                WearHomeContent(
                    homeState = home,
                    recordingState = RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.NOT_ENOUGH_STORAGE),
                )
            }
        }

        composeRule.onNodeWithText("Watch storage is full.\nFree up space").assertIsDisplayed()
    }
}
