package app.logdate.feature.editor.ui.handwriting

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.platform.LocalReduceMotionOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryCreationDockTest {
    @Test
    fun typingClosesSeparateCreationAreaWithGeometryMotionAndNoStaleHitsOrFocusJump() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = mutableStateOf(TextBlockUiState())
            var modeTaps = 0
            setContent {
                MaterialTheme(colorScheme = lightColorScheme(surfaceContainerLow = Color.Blue, surfaceContainerHigh = Color.Red)) {
                    EntryPageChrome(
                        EditorState(blocks = listOf(block.value), expandedBlockId = block.value.id),
                        EntryPageOverviewState(false),
                        EntryStartMode.TEXT,
                        {},
                        {},
                        { modeTaps++ },
                        header = {},
                    ) {
                        MainEditorContent(
                            BlocksUiState(
                                blocks = listOf(block.value),
                                expandedBlockId = block.value.id,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = { block.value = it as TextBlockUiState },
                                onCreateBlock = { _, _ -> block.value },
                                onDeleteBlock = {},
                            ),
                            shouldReturnToPickerOnBack = false,
                            onDismissExpanded = {},
                            expandAnySelectedBlock = true,
                            floatingChrome = true,
                        )
                    }
                }
            }
            val field = onNodeWithTag("editor_text_input")
            val identity = field.fetchSemanticsNode().id
            val initial = onNodeWithTag("entry_capture_tools").getUnclippedBoundsInRoot()
            assertTrue(field.getUnclippedBoundsInRoot().bottom <= initial.top, "Creation controls must be outside editable content")
            val initialCorner = onRoot().captureToImage().toPixelMap()[(initial.left.value + 6).toInt(), (initial.top.value + 6).toInt()]
            assertTrue(
                initialCorner.blue > 0.9f && initialCorner.red < 0.1f,
                "Rounded creation surface must remain separate from the document",
            )
            mainClock.autoAdvance = false
            field.performTextInput("A memory to preserve")
            mainClock.advanceTimeByFrame()
            onNodeWithContentDescription("Handwrite").assertIsNotEnabled()
            mainClock.advanceTimeBy(96)
            val during = onNodeWithTag("entry_capture_tools").getUnclippedBoundsInRoot()
            assertTrue(
                field.getUnclippedBoundsInRoot().bottom <= during.top,
                "Intermediate controls must stay outside the writing viewport",
            )
            val corner = onRoot().captureToImage().toPixelMap()[(during.left.value + 6).toInt(), (during.top.value + 6).toInt()]
            assertTrue(corner.red > 0.1f, "Painted corner must evolve in shape during exit")
            assertTrue(
                during.bottom - during.top > 0.dp && during.bottom - during.top < initial.bottom - initial.top,
                "Exit must change geometry",
            )
            onNodeWithContentDescription("Handwrite").performTouchInput { click() }
            assertEquals(0, modeTaps, "An exiting chooser cannot convert a memory that now has content")
            field.assertIsFocused()
            assertEquals(identity, field.fetchSemanticsNode().id)
            field.performTextClearance()
            mainClock.advanceTimeBy(64)
            field.performTextInput("Keep this instead")
            mainClock.advanceTimeBy(1200)
            onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
            assertEquals("Keep this instead", block.value.content)
            assertEquals(identity, field.fetchSemanticsNode().id)
            field.assertIsFocused()
            mainClock.autoAdvance = true
            field.performTextClearance()
            waitForIdle()
            onNodeWithContentDescription("Handwrite").performTouchInput { click() }
            assertEquals(1, modeTaps, "Clearing the content must restore usable creation choices after entrance")
        }

    @Test
    fun reducedMotionClosesChooserWithoutIntermediateLayout() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = mutableStateOf(TextBlockUiState())
            setContent {
                CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                    MaterialTheme {
                        EntryPageChrome(
                            EditorState(blocks = listOf(block.value), expandedBlockId = block.value.id),
                            EntryPageOverviewState(false),
                            EntryStartMode.TEXT,
                            {},
                            {},
                            {},
                            header = {},
                        ) {}
                    }
                }
            }
            mainClock.autoAdvance = false
            runOnIdle { block.value = block.value.copy(content = "A retained memory") }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
            mainClock.autoAdvance = true
        }

    @Test
    fun completedOverviewKeepsExplicitAddMemoryInsteadOfTypeChoices() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = TextBlockUiState(content = "A retained memory")
            var additions = 0
            setContent {
                MaterialTheme {
                    EntryPageChrome(
                        EditorState(blocks = listOf(block)),
                        EntryPageOverviewState(false),
                        EntryStartMode.TEXT,
                        {},
                        {},
                        {},
                        header = {},
                        onAddMemory = { additions++ },
                    ) {}
                }
            }
            onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
            onNodeWithText("Add to entry").assertIsDisplayed().performClick()
            assertEquals(1, additions)
        }
}
