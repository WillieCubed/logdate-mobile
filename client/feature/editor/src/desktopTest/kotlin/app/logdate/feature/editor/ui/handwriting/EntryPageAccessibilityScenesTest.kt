package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.foldable.FoldablePosture
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryPageAccessibilityScenesTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val font: Float,
) {
    @Test
    fun captureControlsRemainReachableAndNamed() =
        runDesktopComposeUiTest(
            width = width,
            height = height,
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = if (name == "reduced-motion") 0f else 1f
                },
        ) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, font),
                    LocalLayoutDirection provides if (name.contains("rtl")) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    EntryPageStudy(
                        focused = true,
                        dark = name == "dark",
                        fold =
                            foldFixture(
                                if (name == "book-rtl") FoldablePosture.Book else FoldablePosture.Standard,
                                width,
                                height,
                            ),
                    )
                }
            }
            waitForIdle()
            StoryCaptureMode.entries.forEach { mode ->
                val bounds = onNodeWithContentDescription(mode.name).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width >= 48f && bounds.height >= 48f, "$name $mode needs a 48dp target: $bounds")
                assertTrue(bounds.left >= 0f && bounds.right <= width, "$name $mode must fit the viewport")
            }
            val output = File(System.getenv("DISK_JANITOR_ARTIFACT_ROOT") ?: "build/reports", "entry-accessibility").apply { mkdirs() }
            File(output, "$name.png").writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
            onNodeWithContentDescription("Return to entry").performClick()
            onNodeWithTag("entry_mode_overview").assertExists()
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() =
            listOf(
                arrayOf<Any>("compact", 320, 640, 1f),
                arrayOf<Any>("large-text", 360, 800, 2f),
                arrayOf<Any>("landscape", 800, 360, 1f),
                arrayOf<Any>("tablet", 1280, 800, 2f),
                arrayOf<Any>("rtl", 360, 800, 1f),
                arrayOf<Any>("book-rtl", 1000, 800, 1f),
                arrayOf<Any>("dark", 360, 800, 1f),
                arrayOf<Any>("reduced-motion", 360, 800, 1f),
            )
    }
}
