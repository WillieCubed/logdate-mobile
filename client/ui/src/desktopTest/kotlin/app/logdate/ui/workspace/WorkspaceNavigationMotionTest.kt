package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.LocalReduceMotionOverride
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WorkspaceNavigationMotionTest {
    private val destinations =
        listOf(
            WorkspaceDestination("day", "Locations", Icons.Default.Place),
            WorkspaceDestination("places", "Journals", Icons.Default.Place),
        )

    @Test fun resizingFromPhoneToTabletKeepsTheBarOnScreenWhileTheRailAppears() =
        runDesktopComposeUiTest(width = 900, height = 900) {
            var width by mutableStateOf(400.dp)
            setContent {
                LogDateTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.width(width)) { WorkspaceScaffold(destinations, "day", {}) { Text("Content") } }
                    }
                }
            }
            onAllNodesWithText("Journals").assertCountEquals(1)

            mainClock.autoAdvance = false
            width = 900.dp
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            onAllNodesWithText("Journals").assertCountEquals(2)

            mainClock.advanceTimeBy(1000)
            onAllNodesWithText("Journals").assertCountEquals(1)
        }

    @Test fun reducedMotionSwitchesNavigationWithoutAnOverlap() =
        runDesktopComposeUiTest(width = 900, height = 900) {
            var width by mutableStateOf(400.dp)
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.width(width)) { WorkspaceScaffold(destinations, "day", {}) { Text("Content") } }
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            width = 900.dp
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onAllNodesWithText("Journals").assertCountEquals(1)
        }
}
