package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextMemoryHeightTest {
    @Test
    fun `short text stays a writing surface and long text grows beyond its minimum`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val block = mutableStateOf(TextBlockUiState(content = "A short memory."))
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryMemorySequence(
                        uiState =
                            BlocksUiState(
                                blocks = listOf(block.value),
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> block.value },
                                onDeleteBlock = {},
                            ),
                        listState = rememberLazyListState(),
                        onAudioResolverReady = { _, _ -> },
                    )
                }
            }
            waitForIdle()
            val shortBounds = onNodeWithTag("memory_block_${block.value.id}").getUnclippedBoundsInRoot()
            assertTrue(shortBounds.bottom - shortBounds.top >= 240.dp, "A short memory must retain room for writing")
            runOnIdle { block.value = block.value.copy(content = "An additional line of this memory.\n".repeat(40)) }
            waitForIdle()
            val longBounds = onNodeWithTag("memory_block_${block.value.id}").getUnclippedBoundsInRoot()
            assertTrue(
                longBounds.bottom - longBounds.top > 780.dp,
                "Long text must grow naturally rather than being clipped to the minimum",
            )
        }
}
