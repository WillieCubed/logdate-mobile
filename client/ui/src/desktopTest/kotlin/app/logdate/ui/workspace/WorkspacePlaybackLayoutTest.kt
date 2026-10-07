package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorkspacePlaybackLayoutTest {
    @Test
    fun controlsOverlayTheStoryOnWideScreens() = verifyOverlay(LayoutDirection.Ltr)

    @Test
    fun controlsOverlayTheStoryInRtl() = verifyOverlay(LayoutDirection.Rtl)

    private fun verifyOverlay(direction: LayoutDirection) =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        WorkspacePlaybackLayout(
                            focus = { Box(Modifier.fillMaxSize().testTag("story")) },
                            controls = { Box(Modifier.fillMaxSize().testTag("controls")) },
                        )
                    }
                }
            }
            val story = onNodeWithTag("story").fetchSemanticsNode().boundsInRoot
            val controls = onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
            assertEquals(story, controls, "Playback controls must stay over the story")
            assertEquals(0f, story.top, "Home gutters must not reduce playback height")
            assertEquals(800f, story.height)
        }
}
