package app.logdate.feature.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.audio.AudioBlockEditor
import app.logdate.feature.editor.ui.camera.CameraBlockEditor
import app.logdate.feature.editor.ui.content.EditorContentFooter
import app.logdate.feature.editor.ui.content.EmptyEditorStateContent
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.layout.EntryEditorSurface
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.state.EditorRecorderState
import app.logdate.feature.editor.ui.text.TextBlockContent
import app.logdate.feature.editor.ui.video.VideoBlockEditor
import app.logdate.ui.common.conditional
import app.logdate.ui.utils.scrollToEnd
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * The main rendering container for the entry editor.
 *
 * This is responsible for rendering a list of blocks that can be edited,
 */
@Composable
fun MainEditorContent(
    uiState: BlocksUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val blockCount = uiState.blocks.size
    val isEmpty by remember(blockCount) { derivedStateOf { blockCount == 0 } }
    val contentCoroutineScope = rememberCoroutineScope()

    if (isEmpty) {
        // Show the bento box style layout for empty state without LazyColumn
        EmptyEditorStateContent(
            onStartTextBlock = {
                Napier.i("EmptyEditorState: Starting text block")
                uiState.onCreateBlock(BlockType.TEXT)
            },
            onStartPhotoBlock = {
                Napier.i("EmptyEditorState: Starting photo block")
                uiState.onCreateBlock(BlockType.IMAGE)
            },
            onStartAudioBlock = {
                Napier.i("EmptyEditorState: Starting audio block")
                uiState.onCreateBlock(BlockType.AUDIO)
            },
            onStartCameraBlock = {
                Napier.i("EmptyEditorState: Starting camera block")
                uiState.onCreateBlock(BlockType.CAMERA)
            },
            modifier = modifier.fillMaxSize()
        )
    } else {
        // Use LazyColumn only when there are blocks to display
        Box(
            modifier = modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime),
        ) {
            LazyColumn(
                state = listState,
            ) {
                items(uiState.blocks) {
                    // Render each block using the BlockContent composable
                    BlockContent(
                        block = it,
                        onBlockFocused = { blockId ->
                            uiState.textState.onBlockFocused(blockId)
                        },
                        onBlockUpdated = { updatedBlock ->
                            uiState.onUpdateBlock(updatedBlock)
                        },
                        audioState = uiState.audioState,
                        modifier = Modifier.conditional(true) {
                            fillMaxSize()
                        }
                    )
                }
                item {
                    // Footer with expandable toolbar
                    EditorContentFooter(
                        scrollState = listState,
                        onAddBlock = {
                            uiState.onCreateBlock(it)
                            contentCoroutineScope.launch {
                                listState.scrollToEnd()
                            }
                        }
                    )
                }
            }
        }
    }
}


/**
 * A facade renderer for some editor content.
 *
 * This composable is responsible for rendering the content of a single block.
 * It uses the block's data to determine how to display it,
 * and handles user interactions like text changes and focus events.
 */
@Composable
fun <T : EntryBlockUiState> BlockContent(
    block: T,
    onBlockFocused: (Uuid) -> Unit,
    onBlockUpdated: (T) -> Unit,
    audioState: EditorRecorderState? = null,
    modifier: Modifier = Modifier,
) {
    EntryEditorSurface(
        modifier = modifier
            .fillMaxWidth()
    ) {
        when (block) {
            is TextBlockUiState -> {
                TextBlockContent(
                    block = block,
                    isExpanded = true,
                    onTextChanged = { newText ->
                        onBlockUpdated(
                            block.copy(content = newText) as T
                        )
                    },
                    onFocused = {
                        onBlockFocused(block.id)
                    },
                )
            }

            is ImageBlockUiState -> {
                // Use the ImageBlockEditor to handle image display and editing
                app.logdate.feature.editor.ui.image.ImageBlockEditor(
                    block = block,
                    onBlockUpdated = { updatedBlock ->
                        onBlockUpdated(updatedBlock as T)
                    },
                    onDeleteRequested = {
                        // Block deletion would be handled here
                        // In a full implementation, this would call a method on the viewModel
                        // For now it's a placeholder
                    }
                )
            }

            is AudioBlockUiState -> {
                // Use the AudioBlockEditor to handle both recording and playback
                AudioBlockEditor(
                    block = block,
                    onBlockUpdated = { updatedBlock ->
                        onBlockUpdated(updatedBlock as T)
                    },
                    onDeleteRequested = {
                        // Block deletion would be handled here
                        // In a full implementation, this would call a method on the viewModel
                        // For now it's a placeholder
                    }
                )
            }

            is CameraBlockUiState -> {
                // Use CameraBlockEditor for camera-captured media
                CameraBlockEditor(
                    block = block,
                    onBlockUpdated = { updatedBlock ->
                        onBlockUpdated(updatedBlock as T)
                    },
                    onDeleteRequested = {
                        // Block deletion placeholder
                    }
                )
            }

            is VideoBlockUiState -> {
                // Use VideoBlockEditor for video content
                VideoBlockEditor(
                    block = block,
                    onBlockUpdated = { updatedBlock ->
                        onBlockUpdated(updatedBlock as T)
                    },
                    onDeleteRequested = {
                        // Block deletion placeholder
                    }
                )
            }

            else -> {
                Text("Unsupported block type: ${block::class.simpleName}")
            }
        }
    }
}
