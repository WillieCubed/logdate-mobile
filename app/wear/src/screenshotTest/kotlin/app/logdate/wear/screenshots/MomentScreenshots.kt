package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.wear.v2.moments.DeleteDialogContent
import app.logdate.wear.v2.moments.MomentActions
import app.logdate.wear.v2.moments.MomentDetailsPage
import app.logdate.wear.v2.moments.MomentScreen
import app.logdate.wear.v2.moments.MomentUiState
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid

/** Every state of a moment's screen, on the graphite theme. */
class MomentScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_VoiceReady() {
        MomentScene(ready(voice))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_VoicePlayingWithCaption() {
        MomentScene(
            ready(
                voice,
                playback = PlaybackState.Active(voice.uid, positionMs = 6_000L, durationMs = 42_000L, isPaused = false),
                speed = PlaybackSpeed.FASTER,
                skipSilence = true,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_VoicePaused() {
        MomentScene(ready(voice, playback = PlaybackState.Active(voice.uid, positionMs = 7_000L, durationMs = 42_000L, isPaused = true)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S04_VoiceScrubbingLongRecording() {
        MomentScene(
            ready(
                longVoice,
                playback = PlaybackState.Active(longVoice.uid, positionMs = 3_852_000L, durationMs = 4_830_000L, isPaused = false),
                speed = PlaybackSpeed.FASTEST,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_VoicePlayingWithoutTranscript() {
        val note = voice.copy(transcript = null)
        MomentScene(ready(note, playback = PlaybackState.Active(note.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = false)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_VoicePreparing() {
        MomentScene(ready(voice, playback = PlaybackState.Preparing(voice.uid)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S07_VoiceNotOnWatch() {
        MomentScene(ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.NOT_ON_WATCH)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S08_VoiceNoOutput() {
        MomentScene(ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.NO_OUTPUT)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S09_VoiceNoRoom() {
        MomentScene(ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.NO_ROOM)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S10_VoiceUnplayable() {
        MomentScene(ready(voice, playback = PlaybackState.Failed(voice.uid, PlaybackFailure.UNPLAYABLE)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S11_VoiceDetails() {
        DetailsScene(ready(voice))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S12_VoiceDetailsWithoutTranscript() {
        DetailsScene(ready(voice.copy(transcript = null)))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S13_TextDetails() {
        MomentScene(ready(text))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S14_LongTextDetails() {
        MomentScene(ready(longText))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S15_PhotoDetails() {
        MomentScene(ready(photo))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S16_DeleteFailed() {
        DetailsScene(ready(text, deleteFailed = true))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S17_DeleteConfirmation() {
        LogDateWearTheme(dynamicColor = false) {
            AppScaffold(timeText = {}) {
                DeleteDialogContent(onDismiss = {}, onConfirm = {})
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S18_Missing() {
        MomentScene(MomentUiState.Missing)
    }
}

private val momentZone = TimeZone.currentSystemDefault()
private val momentTime = LocalDateTime(2026, 10, 3, 16, 15).toInstant(momentZone)

private val golden = NoteLocation(place = NotePlace(Uuid.parse("00000000-0000-4000-8000-000000000001"), "Golden Gate Park", 37.77, -122.45))

private val voice =
    JournalNote.Audio(
        mediaRef = "audio/lake.m4a",
        durationMs = 42_000L,
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        location = golden,
        transcript =
            TranscriptDocument(
                status = TranscriptDocumentStatus.FINAL,
                segments =
                    listOf(
                        TranscriptSegment(segmentId = "a", text = "Walked to the lake after lunch", startMs = 0L, endMs = 4_000L),
                        TranscriptSegment(segmentId = "b", text = "Two herons on the far bank", startMs = 5_000L, endMs = 9_000L),
                    ),
            ),
    )

private val longVoice =
    JournalNote.Audio(
        mediaRef = "audio/long.m4a",
        durationMs = 4_830_000L,
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        transcript =
            TranscriptDocument(
                status = TranscriptDocumentStatus.FINAL,
                segments =
                    listOf(
                        TranscriptSegment(
                            segmentId = "a",
                            text = "Two herons on the far bank and the light doing that thing over the water again",
                            startMs = 3_845_000L,
                            endMs = 3_860_000L,
                        ),
                    ),
            ),
    )

private val text =
    JournalNote.Text(
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        content = "Coffee before anyone else was up",
        location = golden,
    )

private val longText =
    JournalNote.Text(
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        content =
            "Coffee before anyone else was up. The kitchen was cold and the light came in low across the table. " +
                "Wrote three pages about the trip and then crossed out two of them.",
    )

private val photo =
    JournalNote.Image(
        mediaRef = "photos/lake.jpg",
        creationTimestamp = momentTime,
        lastUpdated = momentTime,
        caption = "The far bank at sunset",
        location = golden,
    )

private fun ready(
    note: JournalNote,
    playback: PlaybackState = PlaybackState.Idle,
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    skipSilence: Boolean = false,
    deleteFailed: Boolean = false,
) = MomentUiState.Ready(note, momentZone, playback, speed, skipSilence, deleteFailed)

/** The voice player leaves the time off so the progress ring stays clear; every other page shows it. */
@Composable
private fun MomentScene(state: MomentUiState) {
    val onPlayer = state is MomentUiState.Ready && state.note is JournalNote.Audio
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { if (!onPlayer) TimeText(timeSource = MomentTimeSource) }) {
            MomentScreen(state = state, actions = MomentActions(onDelete = {}), timeSource = MomentTimeSource)
        }
    }
}

@Composable
private fun DetailsScene(state: MomentUiState.Ready) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { TimeText(timeSource = MomentTimeSource) }) {
            MomentDetailsPage(state = state, onDelete = {}, timeSource = MomentTimeSource)
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object MomentTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
