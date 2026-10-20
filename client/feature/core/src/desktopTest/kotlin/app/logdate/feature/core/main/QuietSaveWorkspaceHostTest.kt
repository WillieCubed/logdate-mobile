package app.logdate.feature.core.main

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.workspace.WorkspaceDestination
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuietSaveWorkspaceHostTest {
    @Test
    fun `phone workspace shows the save acknowledgement and its action`() = assertWorkspaceMessage(411)

    @Test
    fun `tablet workspace shows the save acknowledgement and its action`() = assertWorkspaceMessage(1280)

    private fun assertWorkspaceMessage(width: Int) =
        runDesktopComposeUiTest(width = width, height = 891) {
            val host = SnackbarHostState()
            var opened = false
            setContent {
                LogDateTheme {
                    LaunchedEffect(Unit) {
                        opened = host.showSnackbar("Recording saved", "Open draft") == SnackbarResult.ActionPerformed
                    }
                    HomeWorkspaceComposition(
                        destinations = listOf(WorkspaceDestination("Timeline", "Timeline", HomeRouteDestination.Timeline.selectedIcon)),
                        selectedKey = "Timeline",
                        onSelect = {},
                        snackbarHost = { SnackbarHost(host) },
                    ) { Text("Timeline content") }
                }
            }
            onNodeWithText("Recording saved").assertIsDisplayed()
            onNodeWithText("Open draft").assertIsDisplayed().performClick()
            runOnIdle { assertTrue(opened) }
        }
}
