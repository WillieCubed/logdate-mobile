package app.logdate.ui.timeline.newstuff

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineDayUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class TimelineTextEntryTapTest {
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
