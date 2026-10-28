package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceRailSpacingTest {
    private val destinations =
        listOf(
            WorkspaceDestination("timeline", "Timeline", Icons.Default.Place),
            WorkspaceDestination("places", "Places", Icons.Default.Place),
            WorkspaceDestination("journals", "Journals", Icons.Default.Place),
        )

    @Test fun navigationItemsHaveRelatedElementSpacing() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceScaffold(destinations, "places", {}) { Box(Modifier.fillMaxSize().testTag("content")) }
                }
            }
            val timeline = onNode(hasText("Timeline") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val places = onNode(hasText("Places") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(places.top - timeline.bottom >= 8f, "Navigation targets need visible spacing: $timeline / $places")
        }

    @Test fun collapsingWideRailReclaimsItsContentSpace() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceScaffold(destinations, "places", {}) { Box(Modifier.fillMaxSize().testTag("content")) }
                }
            }
            val expanded = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.left
            assertTrue(expanded <= 240f, "Expanded navigation must not create an oversized empty band: $expanded")
            onNodeWithContentDescription("Collapse navigation").performClick()
            val collapsed = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.left
            assertTrue(collapsed <= 112f, "Collapsed rail must not reserve an expanded-width slot: $collapsed")
            assertTrue(expanded - collapsed >= 80f, "Collapsing must return space to content: $expanded -> $collapsed")
        }
}
