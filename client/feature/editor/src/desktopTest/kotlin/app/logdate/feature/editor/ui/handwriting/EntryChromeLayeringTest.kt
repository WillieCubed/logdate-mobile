@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class EntryChromeLayeringTest {
    @Test
    fun focusOverlayStaysBelowVisibleTappableChromeWhenInterrupted() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = TextBlockUiState(content = "The walk beside the water was quiet.")
            val selected = mutableStateOf<Uuid?>(null)
            val overview = EntryPageOverviewState(false)
            setContent {
                LogDateTheme(dynamicColor = false) {
                    SharedTransitionLayout {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            EntryPageChrome(
                                EditorState(blocks = listOf(block), expandedBlockId = selected.value),
                                overview,
                                EntryStartMode.TEXT,
                                { selected.value = null },
                                {},
                                {},
                                header = {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .background(Color.Magenta)
                                            .clickable { selected.value = null }
                                            .testTag("chrome"),
                                    )
                                },
                            ) {
                                MainEditorContent(
                                    BlocksUiState(
                                        blocks = listOf(block),
                                        expandedBlockId = selected.value,
                                        availableJournals = emptyList(),
                                        selectedJournalIds = emptyList(),
                                        onBlockFocused = { selected.value = it },
                                        onJournalSelectionChanged = {},
                                        onUpdateBlock = {},
                                        onCreateBlock = { _, _ -> block },
                                        onDeleteBlock = {},
                                    ),
                                    shouldReturnToPickerOnBack = false,
                                    onDismissExpanded = { selected.value = null },
                                    expandAnySelectedBlock = true,
                                    floatingChrome = true,
                                )
                            }
                        }
                    }
                }
            }
            mainClock.autoAdvance = false

            fun assertChrome() {
                val pixel = onRoot().captureToImage().toPixelMap()[200, 32]
                assertTrue(pixel.red > 0.9f && pixel.blue > 0.9f && pixel.green < 0.3f, "Content painted over chrome: $pixel")
            }
            runOnIdle { selected.value = block.id }
            mainClock.advanceTimeBy(96)
            assertChrome()
            onNodeWithTag("chrome").performTouchInput { click() }
            runOnIdle { assertEquals(null, selected.value, "Chrome must receive pointer input during focus expansion") }
            mainClock.advanceTimeBy(64)
            assertChrome()
            runOnIdle { selected.value = block.id }
            mainClock.advanceTimeBy(64)
            assertChrome()
            mainClock.advanceTimeBy(1200)
            runOnIdle { selected.value = null }
            mainClock.advanceTimeBy(96)
            assertChrome()
            mainClock.autoAdvance = true
        }
}
