package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.blocks.MemoryBlockSurface
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryGeometryContractTest {
    @Test
    fun overviewHeaderRetainsCaptureModeSwipe() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            var mode: StoryCaptureMode? = null
            setContent {
                MaterialTheme {
                    EntryFocusPrototypeHeader(null, false, false, {}, {}, null, false, {}, { mode = it }, floating = true)
                }
            }
            onNodeWithTag("entry_mode_overview").performTouchInput { swipeLeft() }
            assertEquals(StoryCaptureMode.Handwrite, mode, "Centering must preserve the existing header gesture")
        }

    @Test
    fun recordingSurfaceKeepsSemanticsIdentityAndSelectionThroughStateUpdates() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = AudioBlockUiState()
            val state = mutableStateOf(block)
            setContent {
                MaterialTheme {
                    MemoryBlockSurface(state.value, true, {}, {}, {}) { Text(state.value.captureState.toString()) }
                }
            }
            val node = onNodeWithTag("memory_block_${block.id}")
            val identity = node.fetchSemanticsNode().id
            for (next in listOf(
                AudioCaptureState.Recording(),
                AudioCaptureState.Stopping(),
                AudioCaptureState.Ready("test://audio", 8000),
            )) {
                runOnIdle { state.value = block.copy(captureState = next) }
                assertEquals(identity, node.fetchSemanticsNode().id, "State updates must not replace the surface")
                node.assertIsSelected()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.bottom - bounds.top >= 120.dp, "Capture state collapsed its memory surface")
            }
        }

    @Test
    fun insertingAnotherMemoryRetainsExistingSurfaceIdentity() =
        runDesktopComposeUiTest(width = 400, height = 1000) {
            val original = TextBlockUiState(content = "Original thought")
            val inserted = TextBlockUiState(content = "New thought")
            val blocks = mutableStateOf(listOf(original))
            setContent {
                MaterialTheme {
                    EntryPageOverview(
                        BlocksUiState(
                            blocks = blocks.value,
                            availableJournals = emptyList(),
                            selectedJournalIds = emptyList(),
                            onBlockFocused = {},
                            onJournalSelectionChanged = {},
                            onUpdateBlock = {},
                            onCreateBlock = { _, _ -> original },
                            onDeleteBlock = {},
                        ),
                        emptyList(),
                        EntryPageOverviewState(false),
                    )
                }
            }
            val identity = onNodeWithTag("memory_block_${original.id}").fetchSemanticsNode().id
            runOnIdle { blocks.value = listOf(inserted, original) }
            assertEquals(
                identity,
                onNodeWithTag("memory_block_${original.id}").fetchSemanticsNode().id,
                "Insertion must not remount an existing memory at its new list position",
            )
            val first = onNodeWithTag("memory_block_${inserted.id}").getUnclippedBoundsInRoot()
            val second = onNodeWithTag("memory_block_${original.id}").getUnclippedBoundsInRoot()
            assertTrue(first.bottom <= second.top, "Memories must retain sequential, non-overlapping positions")
        }

    @Test
    fun blockKeepsMinimumHeightAcrossEmptyLoadingFailedAndReadyContent() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val phase = mutableStateOf(0)
            val block = TextBlockUiState()
            setContent {
                MaterialTheme {
                    MemoryBlockSurface(block, false, {}, {}, {}) {
                        when (phase.value) {
                            0 -> Unit
                            1 -> CircularProgressIndicator()
                            2 -> Text("Could not load this memory")
                            3 -> Text("A quiet walk by the water")
                            else -> Box(Modifier.height(280.dp))
                        }
                    }
                }
            }
            for (state in 0..4) {
                runOnIdle { phase.value = state }
                val bounds = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
                assertTrue(bounds.bottom - bounds.top >= 120.dp, "State $state collapsed the block: $bounds")
                if (state == 4) assertTrue(bounds.bottom - bounds.top >= 280.dp, "Minimum must not cap taller content")
            }
        }

    @Test
    fun chooserStaysAtHeaderCenterWithUnequalActionsAndLargeText() {
        for ((width, fontScale, direction) in listOf(
            Triple(360, 1f, LayoutDirection.Ltr),
            Triple(360, 2f, LayoutDirection.Ltr),
            Triple(280, 1f, LayoutDirection.Ltr),
            Triple(280, 2f, LayoutDirection.Ltr),
            Triple(280, 2f, LayoutDirection.Rtl),
        )) {
            runDesktopComposeUiTest(width = width, height = 800) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalLayoutDirection provides direction) {
                        MaterialTheme {
                            EntryFocusPrototypeHeader(
                                null,
                                true,
                                false,
                                {},
                                {},
                                null,
                                false,
                                {},
                                {},
                                onShowDrafts = {},
                                floating = true,
                            )
                        }
                    }
                }
                val header = onNodeWithTag("entry_mode_overview").getUnclippedBoundsInRoot()
                val chooser = onNodeWithText("Choose journal").getUnclippedBoundsInRoot()
                val save = onNodeWithContentDescription("Save entry").getUnclippedBoundsInRoot()
                val close = onNodeWithContentDescription("Close").getUnclippedBoundsInRoot()
                assertTrue(chooser.right - chooser.left >= 48.dp, "Narrow header must retain a usable chooser: $chooser")
                assertTrue(
                    abs((chooser.left + chooser.right).value - (header.left + header.right).value) <= 1f,
                    "Chooser must center on header, not remaining space: $chooser in $header",
                )
                assertTrue(
                    if (direction == LayoutDirection.Ltr) {
                        chooser.left >= close.right && chooser.right <= save.left
                    } else {
                        chooser.right <= close.left && chooser.left >= save.right
                    },
                    "Chooser must not overlap actions",
                )
            }
        }
    }

    @Test
    fun overviewBlocksShareBothEdgesWithModeSelectorOnPhoneAndWideViewport() {
        for (width in listOf(400, 900)) {
            runDesktopComposeUiTest(width = width, height = 1000) {
                val block = TextBlockUiState(content = "A quiet walk by the water")
                setContent { EntryPageStudy(initialBlocks = listOf(block)) }
                val surface = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
                val first = onNodeWithTag("entry_creation_actions").getUnclippedBoundsInRoot()
                val last = first
                assertTrue(abs((surface.left - first.left).value) <= 1f, "Leading edges differ: $surface, $first")
                assertTrue(abs((surface.right - last.right).value) <= 1f, "Trailing edges differ: $surface, $last")
                assertTrue(surface.bottom - surface.top >= 120.dp, "Production text block must retain the shared minimum")
                mainClock.autoAdvance = false
                onNodeWithTag("memory_block_${block.id}").performClick()
                for (time in listOf(64L, 96L, 1000L)) {
                    mainClock.advanceTimeBy(time)
                    val focused = onNodeWithTag("focused_block_${block.id}").getUnclippedBoundsInRoot()
                    assertTrue(focused.bottom - focused.top >= 120.dp, "Focus transition collapsed block: $focused")
                }
                onNodeWithContentDescription("Return to entry").performClick()
                mainClock.advanceTimeBy(64)
                val returning = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
                assertTrue(returning.bottom - returning.top >= 120.dp, "Return transition collapsed block: $returning")
                mainClock.autoAdvance = true
            }
        }
    }
}
