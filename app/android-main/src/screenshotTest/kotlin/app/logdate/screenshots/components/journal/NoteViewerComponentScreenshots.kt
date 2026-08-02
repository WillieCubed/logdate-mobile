package app.logdate.screenshots.components.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.journals.ui.detail.NoteViewerContent
import app.logdate.feature.journals.ui.detail.NoteViewerShared
import app.logdate.feature.journals.ui.detail.TextNoteViewerContent
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.baseInstant
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

private val sampleShared = NoteViewerShared(
    noteId = Uuid.parse("00000000-0000-0000-0000-000000000030"),
    createdAt = baseInstant,
    lastUpdated = baseInstant,
    location = null,
)

// ─── Note Viewer Content ────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun NoteViewer_TextContent() {
    ScreenshotTheme {
        NoteViewerContent(shared = sampleShared) {
            TextNoteViewerContent(
                text =
                    """# River walk

                        |Today was a **beautiful day**. I watched the sunset paint the sky in shades of orange and pink.
                    """.trimMargin(),
                shared = sampleShared,
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun NoteViewer_TextContent_Dark() {
    ScreenshotTheme(darkTheme = true) {
        NoteViewerContent(shared = sampleShared) {
            TextNoteViewerContent(
                text =
                    """# River walk

                        |Today was a **beautiful day**. I watched the sunset paint the sky in shades of orange and pink.
                    """.trimMargin(),
                shared = sampleShared,
            )
        }
    }
}
