package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceSearchTest {
    @Test fun compactLargeTextKeepsGlobalSearchAndAccountOnOneRow() = verifyCompactAppBar(false)

    @Test fun compactLargeTextKeepsPlaceSearchAndAccountOnOneRow() = verifyCompactAppBar(true)

    private fun verifyCompactAppBar(places: Boolean) =
        runDesktopComposeUiTest(width = 320, height = 568) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    LogDateTheme {
                        WorkspaceSearchHost {
                            if (places) WorkspaceSearchScope("", "Search your places", {})
                            WorkspaceAppBar("Locations", {}, actions = {
                                Box(Modifier.size(48.dp).testTag("account_action"))
                            })
                        }
                    }
                }
            }
            val search = onNodeWithTag("workspace_search").fetchSemanticsNode().boundsInRoot
            val account = onNodeWithTag("account_action").fetchSemanticsNode().boundsInRoot
            assertEquals(search.center.y, account.center.y, 0.5f, "Account must remain beside search")
            assertTrue(search.right < account.left)
            assertEquals(304f, account.right)
        }

    @Test fun placeSearchUsesTheShellFieldAndReleasesItWhenLeaving() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            var query = ""
            var globalSearches = 0
            setContent {
                LogDateTheme {
                    var places by rememberSaveable { mutableStateOf(true) }
                    var text by rememberSaveable { mutableStateOf("") }
                    WorkspaceScaffold(
                        listOf(WorkspaceDestination("locations", "Locations", Icons.Default.Place)),
                        "locations",
                        {},
                        onSearch = { globalSearches++ },
                    ) {
                        if (places) {
                            WorkspaceSearchScope(text, "Search your places", {
                                text = it
                                query = it
                            })
                        }
                        androidx.compose.material3.TextButton({ places = !places }) { Text("Switch section") }
                    }
                }
            }
            onAllNodesWithTag("workspace_search").assertCountEquals(1)
            onNodeWithTag("workspace_search").performTextInput("Library")
            assertEquals("Library", query)
            onNodeWithText("Switch section").performClick()
            onNodeWithTag("workspace_search").performClick()
            assertEquals(1, globalSearches)
            onNodeWithText("Switch section").performClick()
            onNodeWithText("Library").fetchSemanticsNode()
        }

    @Test fun wideSearchStartsAtTheContentEdgeWithAccountAtTheTrailingEdge() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceAppBar("Journals", {}, actions = {
                        Box(Modifier.size(48.dp).testTag("account_action"))
                    })
                }
            }
            val search = onNodeWithTag("workspace_search").fetchSemanticsNode().boundsInRoot
            val account = onNodeWithTag("account_action").fetchSemanticsNode().boundsInRoot
            assertEquals(16f, search.left, "Search must align with the panel framing")
            assertEquals(1264f, account.right, "Account stays at the trailing workspace edge")
            assertTrue(search.right < account.left)
        }

    @Test fun phoneSearchStaysAboveThePanelsAndSurvivesDestinationChanges() = verifySearch(411, 891)

    @Test fun tabletSearchUsesTheSameActionOutsideTheCollectionAndDetail() = verifySearch(1280, 800)

    private fun verifySearch(
        width: Int,
        height: Int,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        var searches = 0
        setContent {
            LogDateTheme {
                var selected by rememberSaveable { mutableStateOf("journals") }
                WorkspaceScaffold(
                    listOf(
                        WorkspaceDestination("journals", "Journals", Icons.Default.Place),
                        WorkspaceDestination("locations", "Locations", Icons.Default.Place),
                    ),
                    selected,
                    { selected = it },
                    onSearch = { searches++ },
                ) {
                    AdaptiveWorkspaceLayout(
                        browse = { WorkspacePanel { Text("Collection") } },
                        focus = { WorkspacePanel { Box(Modifier.fillMaxSize().testTag("working_panel")) } },
                    )
                }
            }
        }
        val search = onNodeWithTag("workspace_search")
        assertTrue(
            search.fetchSemanticsNode().boundsInRoot.bottom <= onNodeWithTag("working_panel").fetchSemanticsNode().boundsInRoot.top,
        )
        search.performClick()
        onNodeWithText("Locations").performClick()
        onAllNodesWithTag("workspace_search").assertCountEquals(1)
        search.performClick()
        assertEquals(2, searches)
    }
}
