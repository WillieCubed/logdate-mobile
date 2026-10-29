package app.logdate.ui.timeline.newstuff

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineDayUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class TimelineTextEntryTapTest {
    @Test
    fun tappingPhotoOpensItsEntryExactlyOnce() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val noteId = Uuid.random()
            val opened = mutableListOf<Uuid>()
            val days = mutableListOf<LocalDate>()
            setContent {
                LogDateTheme {
                    TimelineDayListItem(
                        item =
                            TimelineDayUiState(
                                summary = "",
                                notes = listOf(ImageNoteUiState(noteId, "file:///photo.png", Instant.fromEpochMilliseconds(0))),
                                moments = listOf(MomentUiState("photo", "", media = listOf(MomentMediaUiState("file:///photo.png")))),
                            ),
                        layoutMode = TimelineDayLayoutMode.COMPACT,
                        pendingMomentContent = {},
                        onOpenDay = { days += it },
                        onOpenTextNote = { opened += it },
                    )
                }
            }
            onNodeWithContentDescription("Photo on the timeline").performTouchInput { click() }
            assertEquals(listOf(noteId), opened)
            assertEquals(emptyList(), days)
        }

    @Test
    fun tappingTextOpensItsEntryInsteadOfOnlyTheDay() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val noteId = Uuid.random()
            var openedNote: Uuid? = null
            var openedDay: LocalDate? = null
            val day = LocalDate(2026, 10, 13)
            setContent {
                LogDateTheme {
                    TimelineDayListItem(
                        item =
                            TimelineDayUiState(
                                summary = "",
                                date = day,
                                moments =
                                    listOf(
                                        MomentUiState(
                                            id = "moment",
                                            label = "",
                                            textSnippet = "A moment worth keeping",
                                            textNoteId = noteId,
                                        ),
                                    ),
                            ),
                        layoutMode = TimelineDayLayoutMode.COMPACT,
                        pendingMomentContent = {},
                        onOpenDay = { openedDay = it },
                        onOpenTextNote = { openedNote = it },
                    )
                }
            }

            onNodeWithText("A moment worth keeping").performClick()
            assertEquals(noteId, openedNote)
            assertEquals(null, openedDay)
        }
}
