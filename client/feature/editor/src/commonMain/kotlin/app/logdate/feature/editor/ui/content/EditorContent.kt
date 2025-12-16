@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.audio.AudioBlockContent
import app.logdate.feature.editor.ui.audio.AudioBlockEditor
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.overscrollDetector
import app.logdate.feature.editor.ui.layout.rememberOverscrollDetector
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.text.TextBlockContent
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Main content area for the entry editor.
 * Currently focused only on text input mode while preserving audio functionality in the codebase.
 *
 * @param uiState Centralized UI state holder containing all necessary state and callbacks
 * @param modifier Optional modifier for customization
 */
@Deprecated("Use MainEditorContent instead, this is a legacy version focused on text input only")
@Composable
fun EditorContent(
    uiState: BlocksUiState,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val blocks = uiState.blocks

    // Request empty block at the top level if needed
    LaunchedEffect(blocks.isEmpty()) {
        if (blocks.isEmpty()) {
            // TODO: replace in favor of a more interactive way of specifying first content
            // Maybe do some kind of full-container live visualizing bento board to choose content
            Napier.d("No blocks found, requesting initial block creation at top level")
            uiState.requestEmptyBlock()
        }
    }

    // Simplified content - just showing text editor directly
    // We're keeping the HorizontalPager for structure, but it only has one page now
    HorizontalPager(
        state = uiState.pagerState,
        userScrollEnabled = false, // Disable scrolling since we only have text mode
        modifier = modifier.fillMaxSize()
    ) { _ ->
        // Always render text editor content
        RenderTextEditorContent(
            blocks = uiState.blocks,
            expandedBlockId = Uuid.random(), // TODO: Remove
            onTextChanged = { block, newText ->
                if (block is TextBlockUiState) {
                    uiState.textState.onTextChanged(block, newText)
                }
            },
            onBlockFocused = uiState.textState.onBlockFocused,
            onAddBlock = { blockType -> uiState.onCreateBlock(blockType); Unit }
        )

        // NOTE: Audio functionality is preserved in the codebase but not shown in UI
        // EditorAudioWrapper(audioState = uiState.audioState) is still available for future use
    }
}

/**
 * Renders the text editor content with the list of text blocks.
 */
@Composable
private fun RenderTextEditorContent(
    blocks: List<EntryBlockUiState>,
    expandedBlockId: Uuid?,
    onTextChanged: (EntryBlockUiState, String) -> Unit,
    onBlockFocused: (Uuid) -> Unit,
    onAddBlock: (BlockType) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    // Create the overscroll detector for the footer
    val overscrollDetector = rememberOverscrollDetector(
        scrollState = scrollState,
        onOverscrollReleased = { amount, threshold ->
            // Handled by EditorContentFooter, not needed here
        }
    )

    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        // Main column with content and footer
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Content area with blocks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        // Connect the overscroll detector to the scroll
                        .overscrollDetector(overscrollDetector)
                        .verticalScroll(scrollState)
                ) {
                    // Show empty state or blocks
                    if (blocks.isEmpty()) {
                        // Empty state message
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start typing to create your first entry",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        // Display all blocks
                        blocks.forEach { block ->
                            when (block) {
                                is TextBlockUiState -> {
                                    TextBlockContent(
                                        block = block,
                                        isExpanded = block.id == expandedBlockId,
                                        onTextChanged = { newText ->
                                            Napier.d("Block text changed to: '$newText', block ID: ${block.id}")
                                            onTextChanged(block, newText)
                                        },
                                        onFocused = {
                                            onBlockFocused(block.id)
                                        },
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                                is AudioBlockUiState -> {
                                    // We're using AudioBlockEditor instead of AudioBlockContent directly
                                    // because the editor handles ViewModel access properly
                                    AudioBlockEditor(
                                        block = block,
                                        onBlockUpdated = { updatedBlock ->
                                            onTextChanged(block, "") // This is a dummy call to match the API
                                        },
                                        onDeleteRequested = {
                                            // Block deletion would be handled here
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                // Other block types would go here
                                else -> {
                                    Text("Unsupported block type: ${block::class.simpleName}")
                                }
                            }
                        }
                    }
                }
            }
            
            // Footer with expandable toolbar
            EditorContentFooter(
                onAddBlock = onAddBlock,
                scrollState = rememberScrollState(),
            )
        }
    }
}