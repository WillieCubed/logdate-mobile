package app.logdate.client.e2e

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.ui.audio.TranscriptionProvider
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.DayPresentation
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineAudioSectionUiState
import app.logdate.ui.timeline.TimelineDayCardLayout
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.newstuff.EndOfTimelineUiState
import app.logdate.ui.timeline.newstuff.TimelineList
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineTranscriptE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun legacyAudioCard_showsSentenceExcerpt_andExpandsInlineTranscript() {
        val noteId = Uuid.random()
        val firstSentence = "This is a deliberately long opening sentence for the transcript."
        val secondSentence = "The hidden follow-up sentence appears after expansion."
        val transcript = "$firstSentence $secondSentence"

        composeRule.setContent {
            TimelineTestContent(
                items =
                    listOf(
                        legacyAudioDay(noteId = noteId),
                    ),
                transcriptionState =
                    TranscriptionState(
                        getTranscriptionText = { requestedNoteId ->
                            transcript.takeIf { requestedNoteId == noteId }
                        },
                    ),
            )
        }

        composeRule.onNodeWithText(firstSentence).assertExists()
        composeRule.onNodeWithText(secondSentence, substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("Preview Transcript").performClick()

        composeRule.onNodeWithText("Hide Transcript").assertExists()
        composeRule.onNodeWithText(secondSentence, substring = true).assertExists()
    }

    @Test
    fun momentAudioCard_usesSentenceBoundaries_forCollapsedPreview() {
        val secondSentence = "Here is the useful detail that should still show."
        val thirdSentence = "This sentence should stay hidden while collapsed."
        val transcript = "Yep. $secondSentence $thirdSentence"

        composeRule.setContent {
            TimelineTestContent(
                items =
                    listOf(
                        semanticAudioDay(transcript = transcript),
                    ),
            )
        }

        composeRule.onNodeWithText(secondSentence, substring = true).assertExists()
        composeRule.onNodeWithText(thirdSentence, substring = true).assertDoesNotExist()
    }

    @Test
    fun momentAudioCard_expandsInlineTranscript_whenPreviewRequested() {
        val firstSentence = "This is another long first sentence for a semantic moment."
        val secondSentence = "The rest of the transcript should appear after tapping preview."
        val transcript = "$firstSentence $secondSentence"

        composeRule.setContent {
            TimelineTestContent(
                items =
                    listOf(
                        semanticAudioDay(transcript = transcript),
                    ),
            )
        }

        composeRule.onNodeWithText(secondSentence, substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("Preview Transcript").performClick()

        composeRule.onNodeWithText("Hide Transcript").assertExists()
        composeRule.onNodeWithText(secondSentence, substring = true).assertExists()
    }
}

private fun legacyAudioDay(noteId: Uuid): TimelineDayUiState =
    TimelineDayUiState(
        summary = "Voice note day",
        supportingSummary = "A day with a transcribed voice note",
        date = LocalDate(2026, 3, 25),
        layout = TimelineDayCardLayout.VOICE_LED,
        heroSection =
            TimelineAudioSectionUiState(
                label = "Recorded",
                note =
                    AudioNoteUiState(
                        noteId = noteId,
                        uri = "file:///voice-note.m4a",
                        timestamp = Instant.parse("2026-03-25T18:00:00Z"),
                        duration = 42_000,
                    ),
            ),
    )

private fun semanticAudioDay(transcript: String): TimelineDayUiState =
    TimelineDayUiState(
        summary = "Semantic timeline day",
        supportingSummary = "A timeline moment includes an audio note",
        date = LocalDate(2026, 3, 24),
        moments =
            listOf(
                MomentUiState(
                    id = "moment-audio",
                    label = "Afternoon walk",
                    textSnippet = "Stopped to record a quick thought.",
                    audio =
                        MomentAudioUiState(
                            uri = "file:///moment-note.m4a",
                            durationMs = 18_000,
                            transcript = transcript,
                        ),
                    isHero = true,
                ),
            ),
        dayPresentation = DayPresentation.FLOWING,
    )

@Composable
private fun TimelineTestContent(
    items: List<TimelineDayUiState>,
    transcriptionState: TranscriptionState = TranscriptionState(),
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        TranscriptionProvider(state = transcriptionState) {
            TimelineList(
                items = items,
                endOfTimelineState = EndOfTimelineUiState.DiscoveryEasterEgg,
                onOpenDay = {},
            )
        }
    }
}
