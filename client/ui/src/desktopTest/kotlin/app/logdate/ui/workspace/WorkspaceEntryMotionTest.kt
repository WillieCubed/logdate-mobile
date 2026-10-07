@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.ui.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceEntryMotionTest {
    @Test
    fun `mobile create action shares its container with the entry destination`() = verifyCreateMotion(Size(390f, 780f))

    @Test
    fun `rail create action shares its container with the entry destination`() = verifyCreateMotion(Size(800f, 780f))

    @Test
    fun `sidebar create action shares its container with the entry destination`() = verifyCreateMotion(Size(1440f, 900f))

    private fun verifyCreateMotion(size: Size) =
        runSkikoComposeUiTest(size = size) {
            var editor by mutableStateOf(false)
            var active = false
            mainClock.autoAdvance = false
            setContent {
                LogDateTheme(dynamicColor = false) {
                    SharedTransitionLayout {
                        active = isTransitionActive
                        val shared = this
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            AnimatedContent(editor) { destination ->
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                    if (destination) {
                                        with(shared) {
                                            Surface(
                                                Modifier.fillMaxSize().sharedBounds(
                                                    rememberSharedContentState(TransitionKeys.FAB_TO_EDITOR_TRANSITION),
                                                    this@AnimatedContent,
                                                ),
                                            ) { Box(Modifier.fillMaxSize()) }
                                        }
                                    } else {
                                        WorkspaceScaffold(
                                            destinations = listOf(WorkspaceDestination("home", "Timeline", Icons.Default.Home)),
                                            selectedKey = "home",
                                            onSelect = {},
                                            onCreate = { editor = true },
                                            content = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Add a memory").performClick()
            mainClock.advanceTimeBy(80)
            waitForIdle()
            assertTrue(active, "The Home create surface has no matching shared element at $size")
        }
}
