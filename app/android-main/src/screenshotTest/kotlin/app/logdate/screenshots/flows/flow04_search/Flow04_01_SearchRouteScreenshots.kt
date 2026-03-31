package app.logdate.screenshots.flows.flow04_search

import androidx.compose.runtime.Composable
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

private val results =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000061"),
            content = "Captured the last train home and wrote down the feeling before I forgot it.",
            created = ScreenshotTestData.baseInstant,
            contentType = SearchResultType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000062"),
            content = "Voice memo about the route screenshot rollout and the remaining gaps in settings.",
            created = ScreenshotTestData.baseInstant,
            contentType = SearchResultType.TRANSCRIPTION,
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_SearchEmpty() {
    ScreenshotTheme {
        SearchScreenContent(
            searchResults = emptyList(),
            onQueryChange = {},
            onNavigateToDay = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_SearchNoResults() {
    ScreenshotTheme {
        SearchScreenContent(
            searchResults = emptyList(),
            onQueryChange = {},
            onNavigateToDay = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_SearchWithResults() {
    ScreenshotTheme {
        SearchScreenContent(
            searchResults = results,
            onQueryChange = {},
            onNavigateToDay = {},
            onGoBack = {},
        )
    }
}
