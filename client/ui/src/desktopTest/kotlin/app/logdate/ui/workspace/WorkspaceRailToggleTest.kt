package app.logdate.ui.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WorkspaceRailToggleTest {
    private val destinations =
        listOf(
            WorkspaceDestination("day", "Locations", Icons.Default.Place),
            WorkspaceDestination("places", "Journals", Icons.Default.Place),
        )

    @Test fun aWideWindowStartsExpandedAndTheMenuButtonCollapsesIt() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent { LogDateTheme { WorkspaceScaffold(destinations, "day", {}) { Text("Content") } } }
            onNodeWithContentDescription("Collapse navigation").assertIsDisplayed().performClick()
            onNodeWithContentDescription("Expand navigation").assertIsDisplayed()
        }

    @Test fun aMediumWindowStartsCollapsedAndTheMenuButtonExpandsIt() =
        runDesktopComposeUiTest(width = 800, height = 900) {
            setContent { LogDateTheme { WorkspaceScaffold(destinations, "day", {}) { Text("Content") } } }
            onNodeWithContentDescription("Expand navigation").assertIsDisplayed().performClick()
            // The expanded rail overlays the content while the collapsed rail stays in place under it.
            onAllNodesWithContentDescription("Collapse navigation").onFirst().assertIsDisplayed()
        }
}
