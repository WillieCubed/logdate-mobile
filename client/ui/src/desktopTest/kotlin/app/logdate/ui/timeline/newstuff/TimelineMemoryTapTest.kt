package app.logdate.ui.timeline.newstuff

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.shared.model.HandwritingDocument
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.HandwritingNoteUiState
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineDayUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class TimelineMemoryTapTest {
    @Test fun videoBodyKeepsOneEntryActionAcrossRefreshAndReopen() = checkBody("video")

    @Test fun audioBodyKeepsOneEntryActionAcrossRefreshAndReopen() = checkBody("audio")

    @Test fun handwritingBodyKeepsOneEntryActionAcrossRefreshAndReopen() = checkBody("handwriting")

    private fun checkBody(kind: String) =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val id = Uuid.random()
            val at = Instant.fromEpochMilliseconds(0)
            val moment =
                when (kind) {
                    "video" -> MomentUiState("video", "", media = listOf(MomentMediaUiState("file:///video.mp4", true, id)))
                    "audio" -> MomentUiState("audio", "", audio = MomentAudioUiState("file:///voice.m4a", 4000, "Retained recording", id))
                    else -> MomentUiState("ink", "", handwriting = listOf(HandwritingNoteUiState(id, HandwritingDocument(), at)))
                }
            val day = mutableStateOf(TimelineDayUiState(summary = "", moments = listOf(moment)))
            val shown = mutableStateOf(true)
            val opened = mutableListOf<Uuid>()
            val openedDays = mutableListOf<kotlinx.datetime.LocalDate>()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    if (shown.value) {
                        TimelineDayListItem(
                            day.value,
                            TimelineDayLayoutMode.COMPACT,
                            {},
                            { openedDays += it },
                            { opened += it },
                        )
                    }
                }
            }

            fun tap() {
                when (kind) {
                    "video" -> onNodeWithContentDescription("Video clip on the timeline").performTouchInput { click() }
                    "audio" -> onNodeWithText("Retained recording").performTouchInput { click() }
                    else -> onNodeWithContentDescription("Original handwriting").performTouchInput { click() }
                }
            }
            tap()
            tap()
            runOnIdle { day.value = day.value.copy(summary = "A summary arrived") }
            tap()
            runOnIdle { shown.value = false }
            waitForIdle()
            runOnIdle { shown.value = true }
            tap()
            assertEquals(List(4) { id }, opened)
            assertEquals(emptyList(), openedDays)
        }

    @Test
    fun playbackButtonDoesNotOpenTheEntry() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val id = Uuid.random()
            var plays = 0
            var opens = 0
            setContent {
                CompositionLocalProvider(LocalAudioPlaybackState provides AudioPlaybackState(play = { _, _, _ -> plays++ })) {
                    LogDateTheme(dynamicColor = false) {
                        TimelineDayListItem(
                            TimelineDayUiState(
                                "",
                                moments =
                                    listOf(
                                        MomentUiState(
                                            "audio",
                                            "",
                                            audio = MomentAudioUiState("file:///voice.m4a", 4000, "Recorded words", id),
                                        ),
                                    ),
                            ),
                            TimelineDayLayoutMode.COMPACT,
                            {},
                            {},
                            { opens++ },
                        )
                    }
                }
            }
            onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag("moment-audio-card")),
                useUnmergedTree = true,
            ).onFirst().performClick()
            assertEquals(1, plays)
            assertEquals(0, opens)
        }
}
