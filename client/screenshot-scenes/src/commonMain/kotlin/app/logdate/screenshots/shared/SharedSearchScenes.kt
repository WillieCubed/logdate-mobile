@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState

internal val catalogSearchScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.SearchIdle, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
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
                initialQuery = "",
                onQueryChange = {},
                onCommitSearch = {},
                onResultClick = {},
                onResultOpenDay = {},
                onGoBack = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.SearchSearching, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
            SearchScreenContent(
                searchState = SearchScreenState.Searching(query = SEARCH_QUERY),
                queryText = SEARCH_QUERY,
                initialQuery = SEARCH_QUERY,
                onQueryChange = {},
                onCommitSearch = {},
                onResultClick = {},
                onResultOpenDay = {},
                onGoBack = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.SearchEmpty, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
            SearchScreenContent(
                searchState = SearchScreenState.Empty(query = SEARCH_QUERY),
                queryText = SEARCH_QUERY,
                initialQuery = SEARCH_QUERY,
                onQueryChange = {},
                onCommitSearch = {},
                onResultClick = {},
                onResultOpenDay = {},
                onGoBack = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.SearchResults, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
            SearchScreenContent(
                searchState = SearchScreenState.Results(query = SEARCH_QUERY, results = searchResults),
                queryText = SEARCH_QUERY,
                initialQuery = SEARCH_QUERY,
                onQueryChange = {},
                onCommitSearch = {},
                onResultClick = {},
                onResultOpenDay = {},
                onGoBack = {},
            )
        },
    )
