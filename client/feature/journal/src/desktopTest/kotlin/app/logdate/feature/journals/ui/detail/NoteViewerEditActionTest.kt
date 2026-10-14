package app.logdate.feature.journals.ui.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class NoteViewerEditActionTest {
    @Test
    fun textEntryCanBeOpenedForEditingFromItsViewer() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var editCount = 0
            val shared =
                NoteViewerShared(
                    noteId = Uuid.random(),
                    createdAt = Instant.parse("2026-10-13T12:00:00Z"),
                    lastUpdated = Instant.parse("2026-10-13T12:00:00Z"),
                    location = null,
                )
            setContent {
                LogDateTheme {
                    NoteViewerScaffoldContent(
                        shared = shared,
                        onGoBack = {},
                        onEdit = { editCount++ },
                    ) {
                        TextNoteViewerContent(text = "A moment worth keeping", shared = shared)
                    }
                }
            }

            onNodeWithContentDescription("Edit entry").assertIsDisplayed().performClick()
            assertEquals(1, editCount)
        }
}
