package app.logdate.feature.editor.ui.blocks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryAddSurfaceTest {
    @Test
    fun `press feedback covers the rounded container and clears after expanding`() =
        runSkikoComposeUiTest(size = Size(390f, 320f)) {
            val resting = Color(0xFFB6D4BE)
            val pressed = Color(0xFF7494B8)
            var expanded by mutableStateOf(false)
            var expandedCalls = 0
            var collapsedCalls = 0
            val added = mutableListOf<BlockType>()
            setContent {
                MaterialTheme(
                    colorScheme =
                        lightColorScheme(
                            surfaceContainerHigh = resting,
                            surfaceContainerHighest = pressed,
                            primaryContainer = Color.Red,
                        ),
                ) {
                    EndOfEntryAddControl(
                        progress = if (expanded) 1f else 0f,
                        expanded = expanded,
                        onExpand = {
                            expandedCalls++
                            expanded = true
                        },
                        onCollapse = {
                            collapsedCalls++
                            expanded = false
                        },
                        onAdd = { added += it },
                    )
                }
            }
            onNodeWithTag("add_to_entry").performTouchInput { down(center) }
            mainClock.advanceTimeBy(160)
            waitForIdle()
            val heldPixels = onNodeWithTag("add_memory_surface").captureToImage().toPixelMap()
            assertEquals(
                pressed,
                heldPixels[heldPixels.width / 2, 1],
                "The top of the pill outside the header must share the press feedback",
            )
            assertEquals(
                pressed,
                heldPixels[heldPixels.width - 20, heldPixels.height / 2],
                "The header must share the same contained fill without a ripple",
            )
            onNodeWithTag("add_to_entry").performTouchInput { up() }
            waitForIdle()
            val releasedPixels = onNodeWithTag("add_memory_surface").captureToImage().toPixelMap()
            assertEquals(resting, releasedPixels[releasedPixels.width / 2, 1], "Expanded header must not retain the press highlight")
            assertEquals(resting, releasedPixels[releasedPixels.width - 20, 24], "Released header must not retain ripple feedback")
            assertEquals(1, expandedCalls)
            onNodeWithTag("add_memory_AUDIO").performClick()
            assertEquals(listOf(BlockType.AUDIO), added)
            onNodeWithTag("add_close").performClick()
            assertEquals(1, collapsedCalls)
        }

    @Test
    fun `tapping the rounded surface above the old header opens Add`() =
        runSkikoComposeUiTest(size = Size(390f, 320f)) {
            var expandedCalls = 0
            setContent {
                MaterialTheme { EndOfEntryAddControl(0f, false, { expandedCalls++ }, {}, {}) }
            }
            onNodeWithTag("add_memory_surface").performTouchInput {
                down(Offset(width / 2f, 4.dp.toPx()))
                up()
            }
            assertEquals(1, expandedCalls, "The rounded surface, including its top edge, must be the interaction target")
        }

    @Test
    fun `collapsed label and icon group are centered inside the rounded surface`() =
        runSkikoComposeUiTest(size = Size(390f, 320f)) {
            setContent {
                MaterialTheme { EndOfEntryAddControl(0f, false, {}, {}, {}) }
            }
            val surface = onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
            val label = onNodeWithTag("add_to_entry_label", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(
                (surface.top + surface.bottom) / 2f,
                (label.top + label.bottom) / 2f,
                "The invisible grabber must not offset the collapsed header",
            )
            val groupCenter = (label.left - 32.dp + label.right) / 2f
            assertTrue(
                kotlin.math.abs((groupCenter - (surface.left + surface.right) / 2f).value) <= 0.5f,
                "Icon and label must be centered as one group: $groupCenter in $surface",
            )
        }

    @Test
    fun `light Add surface press and expansion screenshots`() = captureInteraction(darkTheme = false)

    @Test
    fun `dark Add surface press and expansion screenshots`() = captureInteraction(darkTheme = true)

    private fun captureInteraction(darkTheme: Boolean) =
        runSkikoComposeUiTest(size = Size(390f, 320f)) {
            var expanded by mutableStateOf(false)
            val name = if (darkTheme) "dark" else "light"
            var restingColor = Color.Unspecified
            var pressedColor = Color.Unspecified
            mainClock.autoAdvance = false
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = darkTheme) {
                    restingColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    pressedColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(400))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        EndOfEntryAddControl(progress, expanded, { expanded = true }, { expanded = false }, {}, Modifier.padding(8.dp))
                    }
                }
            }
            waitForIdle()
            saveScreenshot("add-surface-$name-resting.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            onNodeWithTag("add_to_entry").performTouchInput { down(center) }
            mainClock.advanceTimeBy(160)
            waitForIdle()
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val held = onNodeWithTag("add_memory_surface").captureToImage().toPixelMap()
            assertEquals(pressedColor, held[held.width / 2, 1], "Held screenshot must show the pressed surface")
            saveScreenshot("add-surface-$name-held.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            onNodeWithTag("add_to_entry").performTouchInput { up() }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val released = onNodeWithTag("add_memory_surface").captureToImage().toPixelMap()
            assertEquals(restingColor, released[released.width / 2, 1], "Released screenshot must show the neutral surface")
            saveScreenshot("add-surface-$name-released.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            mainClock.advanceTimeBy(120)
            waitForIdle()
            saveScreenshot("add-surface-$name-mid-expansion.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            mainClock.advanceTimeBy(500)
            waitForIdle()
            onNodeWithTag("add_close").assertIsDisplayed()
            onNodeWithTag("add_memory_AUDIO").assertIsDisplayed()
            saveScreenshot("add-surface-$name-expanded.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
        }

    private fun saveScreenshot(
        name: String,
        image: Image,
    ) {
        val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
        directory.mkdirs()
        File(directory, name).writeBytes(requireNotNull(image.encodeToData()).bytes)
    }
}
