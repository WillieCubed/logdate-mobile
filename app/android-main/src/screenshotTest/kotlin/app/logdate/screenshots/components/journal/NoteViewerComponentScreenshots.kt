package app.logdate.screenshots.components.journal

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.detail.NoteViewerContent
import app.logdate.feature.journals.ui.detail.NoteViewerShared
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
            Text(
                text = "Today was a beautiful day. I went for a long walk by the river and watched the sunset paint the sky in shades of orange and pink.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
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
            Text(
                text = "Today was a beautiful day. I went for a long walk by the river and watched the sunset paint the sky in shades of orange and pink.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
