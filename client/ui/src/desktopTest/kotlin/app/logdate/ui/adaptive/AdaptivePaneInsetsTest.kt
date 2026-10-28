package app.logdate.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalLayoutApi::class)
class AdaptivePaneInsetsTest {
    @Test
    fun nestedContentConsumesSafeInsetsOnce() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val insets = WindowInsets(20, 24, 36, 48)
            setContent {
                AdaptivePaneLayout(contentWindowInsets = insets, contentPadding = PaddingValues(0.dp)) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                        Box(Modifier.fillMaxSize().testTag("content"))
                    }
                }
            }
            val bounds = onNodeWithTag("content").getBoundsInRoot()
            assertEquals(20.dp, bounds.left)
            assertEquals(24.dp, bounds.top)
            assertEquals(364.dp, bounds.right)
            assertEquals(752.dp, bounds.bottom)
        }

    @Test
    fun hostedPaneDoesNotRepeatParentInsetsOnKeyboardShowAndHide() =
        runDesktopComposeUiTest(width = 800, height = 400) {
            val insets = MutableWindowInsets(WindowInsets(28, 24, 48, 24))
            setContent {
                Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                    AdaptivePaneLayout(contentWindowInsets = insets, contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.fillMaxSize().testTag("content"))
                    }
                }
            }

            fun assertBottom(bottom: Int) {
                val bounds = onNodeWithTag("content").getBoundsInRoot()
                assertEquals(28.dp, bounds.left)
                assertEquals(24.dp, bounds.top)
                assertEquals(752.dp, bounds.right)
                assertEquals(bottom.dp, bounds.bottom)
            }
            assertBottom(376)
            runOnIdle { insets.insets = WindowInsets(28, 24, 48, 180) }
            assertBottom(220)
            runOnIdle { insets.insets = WindowInsets(28, 24, 48, 24) }
            assertBottom(376)
        }
}
