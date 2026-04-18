package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.timeline.WearDayDetailContent
import app.logdate.wear.presentation.timeline.WearDayDetailUiState
import app.logdate.wear.presentation.timeline.WearPlaybackUiState
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented tests for audio playback controls in the day detail screen.
 *
 * Tests the content composable directly with controlled state, verifying
 * that idle, playing, and no-output states render the correct UI elements.
 */
@RunWith(AndroidJUnit4::class)
class WearDayDetailPlaybackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val testNoteId = Uuid.random()

    private fun audioNote(
        uid: Uuid = testNoteId,
        durationMs: Long = 15_000,
    ) = JournalNote.Audio(
        uid = uid,
        creationTimestamp = fixedTime,
        lastUpdated = fixedTime,
        mediaRef = "/test/recording.m4a",
        durationMs = durationMs,
    )

    private fun textNote() =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = "Hello world",
        )

    private fun dayDetail(entries: List<JournalNote> = listOf(audioNote())) =
        WearDayDetailUiState(
            date = LocalDate(2024, 3, 9),
            entries = entries,
        )

    // -----------------------------------------------------------------------
    // Idle state — play button + duration
    // -----------------------------------------------------------------------

    @Test
    fun idleAudioNote_displaysPlayIcon() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }

    @Test
    fun idleAudioNote_displaysDuration() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(audioNote(durationMs = 15_000))),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Voice note 0:15").assertIsDisplayed()
    }

    @Test
    fun idleAudioNote_tapTriggersToggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = { toggled = true },
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play voice note").performClick()
        assertTrue("Tapping audio card should trigger toggle", toggled)
    }

    // -----------------------------------------------------------------------
    // Playing state — stop button + progress
    // -----------------------------------------------------------------------

    @Test
    fun playingAudioNote_displaysStopButton() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState =
                        WearPlaybackUiState.Active(
                            noteId = testNoteId,
                            progress = 0.5f,
                            durationMs = 15_000,
                        ),
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Stop").assertIsDisplayed()
    }

    @Test
    fun playingAudioNote_displaysElapsedTime() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState =
                        WearPlaybackUiState.Active(
                            noteId = testNoteId,
                            progress = 0.5f,
                            durationMs = 60_000,
                        ),
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        // 0.5 * 60000 / 1000 = 30 seconds → "0:30"
        composeRule.onNodeWithText("0:30").assertIsDisplayed()
    }

    @Test
    fun playingAudioNote_stopTriggersToggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState =
                        WearPlaybackUiState.Active(
                            noteId = testNoteId,
                            progress = 0.3f,
                            durationMs = 15_000,
                        ),
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = { toggled = true },
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Stop").performClick()
        assertTrue("Tapping stop should trigger toggle", toggled)
    }

    // -----------------------------------------------------------------------
    // No output state — Bluetooth prompt
    // -----------------------------------------------------------------------

    @Test
    fun noOutput_displaysConnectHeadphonesMessage() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.Unavailable,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Connect headphones to listen").assertIsDisplayed()
    }

    @Test
    fun noOutput_tapOpensBluetoothSettings() {
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.Unavailable,
                    onToggleNote = {},
                    onOpenBluetoothSettings = { opened = true },
                )
            }
        }

        composeRule.onNodeWithText("Connect headphones to listen").performClick()
        assertTrue("Tapping no-output card should open Bluetooth settings", opened)
    }

    // -----------------------------------------------------------------------
    // Mixed entry types
    // -----------------------------------------------------------------------

    @Test
    fun mixedEntries_textNoteShowsContent() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(textNote(), audioNote())),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Hello world").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }

    @Test
    fun differentNoteActive_showsPlayNotStop() {
        val otherNoteId = Uuid.random()
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState =
                        WearPlaybackUiState.Active(
                            noteId = otherNoteId,
                            progress = 0.5f,
                            durationMs = 10_000,
                        ),
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenBluetoothSettings = {},
                )
            }
        }

        // This note is NOT the active one, so it should show play, not stop
        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }
}
