package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.timeline.WearDayDetailContent
import app.logdate.wear.presentation.timeline.WearDayDetailTags
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
 * Instrumented UI tests for audio playback interaction within the Wear OS Day Detail screen.
 *
 * This suite validates the rendering and behavior of the [WearDayDetailContent] when
 * interacting with voice notes. It ensures that the UI correctly transitions between
 * idle, playing, and blocked states (e.g., when no audio output device is connected),
 * verifying that appropriate icons, progress indicators, and instructional prompts
 * are visible and functional.
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
    fun `idle audio note displays play icon`() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onChooseOutput = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }

    @Test
    fun `idle audio note displays duration`() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(audioNote(durationMs = 15_000))),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onChooseOutput = {},
                )
            }
        }

        composeRule.onNodeWithText("Voice note 0:15").assertIsDisplayed()
    }

    @Test
    fun `idle audio note tap triggers toggle`() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = { toggled = true },
                    onChooseOutput = {},
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
    fun `playing audio note displays stop button`() {
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
                    onChooseOutput = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Stop").assertIsDisplayed()
    }

    @Test
    fun `playing audio note displays elapsed time`() {
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
                    onChooseOutput = {},
                )
            }
        }

        // 0.5 * 60000 / 1000 = 30 seconds → "0:30"
        composeRule.onNodeWithText("0:30").assertIsDisplayed()
    }

    @Test
    fun `playing audio note stop triggers toggle`() {
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
                    onChooseOutput = {},
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
    fun `no output displays connect headphones message`() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.Unavailable,
                    onToggleNote = {},
                    onChooseOutput = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(WearDayDetailTags.audioNote(testNoteId))
            .assertIsDisplayed()
            .assertTextContains("Choose an output to listen")
    }

    @Test
    fun `no output tap opens output picker`() {
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.Unavailable,
                    onToggleNote = {},
                    onChooseOutput = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag(WearDayDetailTags.audioNote(testNoteId)).performClick()
        assertTrue("Tapping no-output card should open output picker", opened)
    }

    @Test
    fun `audio output action opens the system picker`() {
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerAndBluetooth,
                    onToggleNote = {},
                    onChooseOutput = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag(WearDayDetailTags.OUTPUT_SUMMARY).assertIsDisplayed().performClick()
        assertTrue("Audio output action should open the system picker", opened)
    }

    @Test
    fun `day without audio does not advertise an output picker`() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(textNote())),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(WearDayDetailTags.OUTPUT_SUMMARY).assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // Mixed entry types
    // -----------------------------------------------------------------------

    @Test
    fun `mixed entries text note shows content`() {
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(textNote(), audioNote())),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onChooseOutput = {},
                )
            }
        }

        composeRule.onNodeWithText("Hello world").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }

    @Test
    fun `text entry opens its full reader`() {
        val note = textNote()
        var opened: Uuid? = null
        composeRule.setContent {
            MaterialTheme {
                WearDayDetailContent(
                    detail = dayDetail(entries = listOf(note)),
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onOpenTextEntry = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Hello world").performClick()
        assertTrue("Tapping text should open the entry", opened == note.uid)
    }

    @Test
    fun `different note active shows play not stop`() {
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
                    onChooseOutput = {},
                )
            }
        }

        // This note is NOT the active one, so it should show play, not stop
        composeRule.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
    }
}
