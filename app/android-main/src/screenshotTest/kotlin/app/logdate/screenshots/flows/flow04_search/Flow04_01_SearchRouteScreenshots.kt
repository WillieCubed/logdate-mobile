package app.logdate.screenshots.flows.flow04_search

import kotlin.uuid.Uuid
import androidx.compose.runtime.Composable
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private const val SEARCH_QUERY = "sun"

private val results =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000061"),
            content = "Caught the sunrise train and wrote down the quiet before the city woke up.",
            created = ScreenshotTestData.baseInstant,
            contentType = SearchContentType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000062"),
            content = "Voice memo on the sunrise trail, the route changes, and what still needs shipping.",
            created = ScreenshotTestData.baseInstant,
            contentType = SearchContentType.TRANSCRIPTION,
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_SearchIdleWithRecents() {
    ScreenshotTheme {
        SearchScreenContent(
            searchState =
                SearchScreenState.Idle(
                    recentSearches =
                        listOf(
                            "sunrise trail",
                            "voice memo",
                            "budget review",
                        ),
                ),
            queryText = "",
            onQueryChange = {},
            onCommitSearch = {},
            onNavigateToDay = {},
            onNavigateToJournal = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_SearchSearching() {
    ScreenshotTheme {
        SearchScreenContent(
            searchState = SearchScreenState.Searching(query = SEARCH_QUERY),
            queryText = SEARCH_QUERY,
            onQueryChange = {},
            onCommitSearch = {},
            onNavigateToDay = {},
            onNavigateToJournal = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_SearchEmpty() {
    ScreenshotTheme {
        SearchScreenContent(
            searchState = SearchScreenState.Empty(query = SEARCH_QUERY),
            queryText = SEARCH_QUERY,
            onQueryChange = {},
            onCommitSearch = {},
            onNavigateToDay = {},
            onNavigateToJournal = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_SearchResults() {
    ScreenshotTheme {
        SearchScreenContent(
            searchState = SearchScreenState.Results(query = SEARCH_QUERY, results = results),
            queryText = SEARCH_QUERY,
            onQueryChange = {},
            onCommitSearch = {},
            onNavigateToDay = {},
            onNavigateToJournal = {},
            onGoBack = {},
        )
    }
}
