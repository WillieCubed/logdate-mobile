package app.logdate.ui.timeline.newstuff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimelineSkeletonLayoutTest {
    @Test
    fun loadingDayLeavesRoomForAnotherEntryOnPhone() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                LogDateTheme {
                    Box(Modifier.fillMaxSize()) { TimelineDaySkeleton(Modifier.testTag("loading-day")) }
                }
            }
            val height = onNodeWithTag("loading-day").fetchSemanticsNode().boundsInRoot.height
            assertTrue(height < 300f, "A loading day should fit compact memory blocks rather than reserve a large fake photo: $height")
        }
}
