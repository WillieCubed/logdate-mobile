package app.logdate.feature.library.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class WorkspaceMediaPagingTest {
    @Test fun returningToTheFirstPhotoUpdatesItsDetails() =
        runDesktopComposeUiTest(width = 411, height = 700) {
            val items = List(2) { MediaViewerItem(Uuid.random(), "missing-photo-$it", false) }
            setContent {
                LogDateTheme {
                    var state by remember { mutableStateOf(MediaViewerState(0, 2, items)) }
                    Box {
                        WorkspaceMediaViewer(
                            "",
                            false,
                            state,
                            { state = state.copy(currentIndex = it) },
                            {},
                            {},
                            Modifier.fillMaxSize().testTag("pager"),
                        )
                        Text("Selected photo ${state.currentIndex}")
                    }
                }
            }
            onNodeWithTag("pager").performTouchInput { swipeLeft() }
            onNodeWithText("Selected photo 1").assertIsDisplayed()
            onNodeWithTag("pager").performTouchInput { swipeRight() }
            onNodeWithText("Selected photo 0").assertIsDisplayed()
        }
}
