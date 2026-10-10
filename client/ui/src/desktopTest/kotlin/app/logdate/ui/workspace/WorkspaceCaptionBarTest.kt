@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceCaptionBarTest {
    private val captionBar = WorkspaceCaptionBar(height = 48.dp, left = 160.dp, right = 144.dp)

    @Test fun searchAndAccountSitInTheCaptionBarBetweenTheSystemControls() = verifyAppBarInCaption(places = false)

    @Test fun placeSearchSitsInTheCaptionBarBetweenTheSystemControls() = verifyAppBarInCaption(places = true)

    private fun verifyAppBarInCaption(places: Boolean) =
        runDesktopComposeUiTest(width = WIDTH, height = 800) {
            setContent { Workspace(captionBar, places = places) }
            val search = onNodeWithTag("workspace_search").bounds()
            val account = onNodeWithTag("account_action").bounds()
            for ((name, bounds) in listOf("Search" to search, "Account" to account)) {
                assertTrue(bounds.top >= 0f && bounds.bottom <= 48f, "$name must fit the 48dp caption bar: $bounds")
                assertTrue(bounds.left >= 160f, "$name must clear the app menu at the left: $bounds")
                assertTrue(bounds.right <= WIDTH - 144f, "$name must clear the window buttons at the right: $bounds")
            }
            val content = onNodeWithText("Content").bounds()
            assertTrue(content.top >= 48f, "Content must start below the caption bar: $content")
            onNodeWithText("LogDate").assertDoesNotExist()
        }

    @Test fun largeTextKeepsTheAppBarBelowTheCaptionBar() =
        runDesktopComposeUiTest(width = WIDTH, height = 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Workspace(captionBar, places = false)
                }
            }
            // The caption bar here is only a value, with no window inset above the app bar, so the
            // full-height search bar shows the app bar stayed in the workspace.
            val search = onNodeWithTag("workspace_search").bounds()
            assertTrue(search.height >= 56f, "Search must not squeeze into the caption bar at 200% text: $search")
        }

    @Test fun withoutACaptionBarTheAppBarStaysInTheWorkspace() =
        runDesktopComposeUiTest(width = WIDTH, height = 800) {
            setContent { Workspace(captionBar = null, places = false) }
            val search = onNodeWithTag("workspace_search").bounds()
            assertTrue(search.height >= 56f, "Search keeps its full height without a caption bar: $search")
            onNodeWithText("LogDate").assertExists()
        }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    @Composable
    private fun Workspace(
        captionBar: WorkspaceCaptionBar?,
        places: Boolean,
    ) {
        LogDateTheme {
            CompositionLocalProvider(LocalWorkspaceCaptionBar provides captionBar) {
                WorkspaceScaffold(
                    listOf(
                        WorkspaceDestination("timeline", "Timeline", Icons.Default.Timeline),
                        WorkspaceDestination("locations", "Places", Icons.Default.Place),
                    ),
                    if (places) "locations" else "timeline",
                    {},
                    onSearch = {},
                    actions = { Box(Modifier.size(40.dp).testTag("account_action")) },
                ) {
                    if (places) WorkspaceSearchScope("", "Search your places", {})
                    Text("Content")
                }
            }
        }
    }

    private companion object {
        const val WIDTH = 1280
    }
}
