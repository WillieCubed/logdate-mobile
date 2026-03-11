package app.logdate.screenshots.components.home_timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.timeline.ui.details.TimelineDetailsEmptyPlaceholder
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Timeline Details Empty ─────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun TimelineDetails_EmptyDetail() {
    ScreenshotTheme {
        TimelineDetailsEmptyPlaceholder()
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TimelineDetails_EmptyDetail_Dark() {
    ScreenshotTheme(darkTheme = true) {
        TimelineDetailsEmptyPlaceholder()
    }
}
