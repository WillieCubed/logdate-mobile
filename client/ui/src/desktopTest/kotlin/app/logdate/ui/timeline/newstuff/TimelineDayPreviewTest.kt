package app.logdate.ui.timeline.newstuff

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.DayPresentation
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class TimelineDayPreviewTest {
    @Test
    fun newestPendingMemoryRemainsVisibleInThePreview() = checkPendingMemory(DayPresentation.FLOWING)

    @Test
    fun newestPendingMemoryRemainsVisibleInAStackedDay() = checkPendingMemory(DayPresentation.STACKED)

    private fun checkPendingMemory(presentation: DayPresentation) =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val at = Instant.parse("2026-10-28T12:00:00Z")
            val mark = MarkedMoment(Uuid.random(), at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
            val earlier = TextNoteUiState(Uuid.random(), "Earlier entry", at - kotlin.time.Duration.parse("1m"))
            val day =
                TimelineDayUiState(
                    "",
                    notes = listOf(earlier),
                    moments = listOf(MomentUiState("pending", "", capturedAt = at, pendingMark = mark)),
                    dayPresentation = presentation,
                )
            setContent {
                LogDateTheme(dynamicColor = false) {
                    TimelineDayListItem(day, TimelineDayLayoutMode.COMPACT, { Text("Add to this memory") }, {})
                }
            }
            onNodeWithText("Add to this memory").assertExists()
        }

    @Test
    fun twoRecentEntriesExpandInPlaceWithoutDuplicatingThePreview() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val notes =
                (1..3).map { index ->
                    TextNoteUiState(
                        Uuid.random(),
                        "Retained entry $index",
                        Instant.fromEpochMilliseconds(
                            index * 60_000L,
                        ),
                    )
                }
            val state =
                mutableStateOf(
                    TimelineDayUiState(
                        "",
                        notes = notes,
                        moments =
                            notes.map {
                                MomentUiState("note-${it.noteId}", "", textSnippet = it.text, textNoteId = it.noteId)
                            },
                    ),
                )
            setContent { LogDateTheme(dynamicColor = false) { TimelineDayListItem(state.value, TimelineDayLayoutMode.COMPACT, {}, {}) } }
            onNodeWithText("Retained entry 3").assertExists()
            onNodeWithText("Retained entry 2").assertExists()
            onNodeWithText("Retained entry 1").assertDoesNotExist()

            onNodeWithText("Show 1 more entry").performClick()

            (1..3).forEach { index -> onAllNodesWithText("Retained entry $index").assertCountEquals(1) }
            runOnIdle { state.value = state.value.copy(summary = "A summary arrived") }
            (1..3).forEach { index -> onAllNodesWithText("Retained entry $index").assertCountEquals(1) }
            onNodeWithText("Show fewer entries").performClick()
            onNodeWithText("Retained entry 1").assertDoesNotExist()
        }
}
