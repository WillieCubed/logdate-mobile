package app.logdate.ui.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.creation.EntryCreationLauncher
import app.logdate.ui.creation.LocalEntryCreationLauncher
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceEntryPullTest {
    @Test
    fun subThresholdPhysicalPullCreatesNoRoute() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val routes = mutableListOf<String?>()
            val launcher = EntryCreationLauncher()
            setContent {
                CompositionLocalProvider(LocalEntryCreationLauncher provides launcher) {
                    LogDateTheme {
                        WorkspaceEntryPull(onCreate = { routes += launcher.currentSourceId }) {
                            LazyColumn(Modifier.fillMaxSize().testTag("timeline")) { item { Text("Memory", Modifier.height(64.dp)) } }
                        }
                    }
                }
            }
            onNodeWithTag("timeline").performTouchInput {
                down(Offset(center.x, 180f))
                moveBy(Offset(0f, 80f), 250)
                up()
            }
            waitForIdle()
            assertEquals(emptyList(), routes)
        }

    @Test
    fun nativeStylePointerCancelCannotCommitAnArmedPull() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            var visits = 0
            setContent {
                LogDateTheme {
                    WorkspaceEntryPull(onCreate = { visits++ }) {
                        LazyColumn(Modifier.fillMaxSize().testTag("timeline")) { item { Text("Memory", Modifier.height(64.dp)) } }
                    }
                }
            }
            onNodeWithTag("timeline").performTouchInput {
                down(Offset(center.x, 180f))
                moveBy(Offset(0f, 250f), 300)
                cancel()
            }
            waitForIdle()
            assertEquals(0, visits)
        }

    @Test
    fun armedPhysicalPullHasARealBoundedSourceAndCommitsOnlyOnRelease() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val routes = mutableListOf<String?>()
            val launcher = EntryCreationLauncher()
            setContent {
                CompositionLocalProvider(LocalEntryCreationLauncher provides launcher) {
                    LogDateTheme {
                        WorkspaceEntryPull(onCreate = { routes += launcher.currentSourceId }) {
                            LazyColumn(Modifier.fillMaxSize().testTag("timeline")) { item { Text("Memory", Modifier.height(64.dp)) } }
                        }
                    }
                }
            }
            onNodeWithTag("timeline").performTouchInput {
                down(Offset(center.x, 180f))
                moveBy(Offset(0f, 100f), 200)
                moveBy(Offset(0f, 150f), 200)
            }
            val source = onNodeWithTag("workspace_entry_pull_source").getUnclippedBoundsInRoot()
            assertTrue(source.right - source.left > 100.dp && source.right - source.left <= 368.dp)
            assertTrue(source.top >= 0.dp && source.bottom < 160.dp)
            assertEquals(emptyList(), routes, "An armed preview must not construct an editor")
            onNodeWithTag("timeline").performTouchInput { up() }
            waitForIdle()
            assertEquals(1, routes.size)
            assertTrue(!routes.single().isNullOrBlank())
            onNodeWithTag("timeline").performTouchInput {
                down(Offset(center.x, 180f))
                moveBy(Offset(0f, 250f), 300)
                up()
            }
            assertEquals(1, routes.size)
        }
}
