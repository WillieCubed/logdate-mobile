package app.logdate.screenshots.components.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.library.ui.LibraryContent
import app.logdate.feature.library.ui.LibraryUiState
import app.logdate.feature.library.ui.detail.MediaDetailContent
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.library.ui.detail.PresenterState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTestData.TABLET
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Library Grid ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LibraryGrid_Empty() {
    ScreenshotTheme {
        LibraryContent(
            state = LibraryUiState.Empty,
            columnCount = 3,
            onItemClick = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LibraryGrid_Content() {
    ScreenshotTheme {
        LibraryContent(
            state = LibraryScreenshotData.gridContent,
            columnCount = 3,
            onItemClick = {},
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    device = PHONE,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun LibraryGrid_Content_Dark() {
    ScreenshotTheme(darkTheme = true) {
        LibraryContent(
            state = LibraryScreenshotData.gridContent,
            columnCount = 3,
            onItemClick = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = TABLET)
@Composable
fun LibraryGrid_Content_Tablet() {
    ScreenshotTheme {
        LibraryContent(
            state = LibraryScreenshotData.gridContent,
            columnCount = 5,
            onItemClick = {},
        )
    }
}

// ─── Media Detail ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MediaDetail_Compact() {
    ScreenshotTheme {
        MediaDetailContent(
            state = LibraryScreenshotData.imageDetail,
            isExpanded = false,
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = TABLET)
@Composable
fun MediaDetail_Expanded() {
    ScreenshotTheme {
        MediaDetailContent(
            state = LibraryScreenshotData.imageDetail,
            isExpanded = true,
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE_LANDSCAPE)
@Composable
fun MediaDetail_Landscape() {
    ScreenshotTheme {
        MediaDetailContent(
            state = LibraryScreenshotData.imageDetail,
            isExpanded = true,
            onBack = {},
        )
    }
}

// ─── Presenter Mode ──────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MediaDetail_Presenting() {
    ScreenshotTheme {
        MediaDetailContent(
            state = LibraryScreenshotData.imageDetail,
            presenterState = LibraryScreenshotData.presenterActive,
            isExpanded = false,
            onBack = {},
        )
    }
}
