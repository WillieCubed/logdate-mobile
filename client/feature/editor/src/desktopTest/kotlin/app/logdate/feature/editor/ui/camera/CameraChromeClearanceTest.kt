package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.handwriting.EntryPageChrome
import app.logdate.feature.editor.ui.handwriting.EntryPageOverviewState
import app.logdate.ui.platform.LocalReduceMotionOverride
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class CameraChromeClearanceTest(
    private val width: Int,
    private val height: Int,
) {
    @Test
    fun cameraRouteAndRealEntryHeaderRemainSeparatePhysicalTargets() =
        runDesktopComposeUiTest(width = width, height = height) {
            val block = CameraBlockUiState()
            var returns = 0
            var routes = 0
            setContent {
                CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                    MaterialTheme {
                        EntryPageChrome(
                            EditorState(blocks = listOf(block), expandedBlockId = block.id),
                            EntryPageOverviewState(false),
                            EntryStartMode.CAMERA,
                            { returns++ },
                            {},
                            {},
                            safeInsets = WindowInsets(0, 24, 0, 20),
                        ) {
                            Box(Modifier.fillMaxSize().background(Color.Black)) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 24.dp)
                                        .cameraHeaderClearance()
                                        .padding(16.dp),
                                ) {
                                    Button({ routes++ }, Modifier.size(120.dp, 48.dp).testTag("camera_route_probe")) {
                                        Text("Camera route")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val back = onNodeWithContentDescription("Return to entry")
            val route = onNodeWithTag("camera_route_probe")
            assertTrue(
                route.fetchSemanticsNode().boundsInRoot.top >= back.fetchSemanticsNode().boundsInRoot.bottom,
                "Camera route must not sit under the real entry header",
            )
            route.performTouchInput { click() }
            back.performTouchInput { click() }
            assertEquals(1, routes)
            assertEquals(1, returns)
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}x{1}")
        fun sizes() = listOf(arrayOf(400, 800), arrayOf(800, 400))
    }
}
