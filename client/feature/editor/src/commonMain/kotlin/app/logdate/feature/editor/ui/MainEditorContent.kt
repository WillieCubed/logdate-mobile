@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.audio.AudioBlockEditor
import app.logdate.feature.editor.ui.camera.CameraBlockEditor
import app.logdate.feature.editor.ui.common.PlatformPredictiveBackHandler
import app.logdate.feature.editor.ui.content.EditorContentFooter
import app.logdate.feature.editor.ui.content.EmptyEditorStateContent
import app.logdate.feature.editor.ui.content.matchingPickerTileIdsFor
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.layout.EntryEditorSurface
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.text.TextBlockContent
import app.logdate.feature.editor.ui.video.VideoBlockEditor
import app.logdate.ui.utils.scrollToEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private sealed interface EditorDisplay {
    data object Empty : EditorDisplay

    data class Expanded(
        val block: EntryBlockUiState,
    ) : EditorDisplay

    data object List : EditorDisplay
}

/**
 * The main rendering container for the entry editor.
 *
 * Three display states — empty, expanded block, scrollable list — are driven by a
 * [SeekableTransitionState] so that the predictive back gesture can scrub the
 * shared-bounds morph in real time before committing or cancelling.
 *
 * @param onDismissExpanded Called when the expanded block should be dismissed (back committed)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MainEditorContent(
    uiState: BlocksUiState,
    shouldReturnToPickerOnBack: Boolean,
    onDismissExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val scope = rememberCoroutineScope()

    val expandedBlock =
        remember(uiState.expandedBlockId, uiState.blocks) {
            uiState.blocks.find { it.id == uiState.expandedBlockId }
        }

    val displayState: EditorDisplay =
        when {
            uiState.blocks.isEmpty() -> EditorDisplay.Empty
            expandedBlock != null -> EditorDisplay.Expanded(expandedBlock)
            else -> EditorDisplay.List
        }

    // SeekableTransitionState lets the predictive back gesture scrub the transition
    val transitionState = remember { SeekableTransitionState(displayState) }

    // Sync ViewModel-driven state changes. displayState is a plain val recomputed on each
    // recomposition, so snapshotFlow can't track it — use the underlying keys as the effect key.
    LaunchedEffect(uiState.expandedBlockId, uiState.blocks.isEmpty()) {
        if (transitionState.targetState != displayState) {
            transitionState.animateTo(displayState)
        }
    }

    val backTarget: EditorDisplay =
        if (shouldReturnToPickerOnBack) {
            EditorDisplay.Empty
        } else {
            EditorDisplay.List
        }
    val pickerTileIds =
        remember(expandedBlock, shouldReturnToPickerOnBack) {
            if (shouldReturnToPickerOnBack) {
                matchingPickerTileIdsFor(expandedBlock)
            } else {
                matchingPickerTileIdsFor(null)
            }
        }

    // Single flow for seek progress — ensures seeks are processed sequentially via collectLatest,
    // dropping intermediate values if a new one arrives before the previous seekTo completes.
    val seekFlow = remember { MutableStateFlow<Float?>(null) }
    LaunchedEffect(backTarget) {
        seekFlow.collectLatest { fraction ->
            if (fraction != null) transitionState.seekTo(fraction, targetState = backTarget)
        }
    }

    PlatformPredictiveBackHandler(
        enabled = displayState is EditorDisplay.Expanded,
        onProgress = { fraction -> seekFlow.value = fraction },
        onBack = {
            scope.launch {
                seekFlow.value = null
                transitionState.animateTo(backTarget)
                onDismissExpanded()
            }
        },
        onCancel = {
            // currentState is the state we were animating from (Expanded), safe without !!
            scope.launch {
                seekFlow.value = null
                transitionState.animateTo(transitionState.currentState)
            }
        },
    )

    val transition = rememberTransition(transitionState, label = "editorDisplay")

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        val sts = this
        CompositionLocalProvider(LocalSharedTransitionScope provides sts) {
            transition.AnimatedContent(
                contentKey = { it::class },
                // The shared container morph is the semantic transition here.
                // Disabling shell-level fades keeps predictive back purely spatial.
                transitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
            ) { target ->
                val avs = this
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides avs) {
                    when (target) {
                        EditorDisplay.Empty -> {
                            EmptyEditorStateContent(
                                onStartTextBlock = { id -> uiState.onCreateBlock(BlockType.TEXT, id) },
                                onStartPhotoBlock = { id -> uiState.onCreateBlock(BlockType.IMAGE, id) },
                                onStartAudioBlock = { id -> uiState.onCreateBlock(BlockType.AUDIO, id) },
                                onStartCameraBlock = { id -> uiState.onCreateBlock(BlockType.CAMERA, id) },
                                textTileId = pickerTileIds.textId,
                                photoTileId = pickerTileIds.photoId,
                                audioTileId = pickerTileIds.audioId,
                                cameraTileId = pickerTileIds.cameraId,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        is EditorDisplay.Expanded -> {
                            // Read the live block from uiState rather than the
                            // transition's target snapshot so that edits (typing)
                            // are reflected immediately without waiting for the
                            // SeekableTransitionState to update.
                            val liveBlock =
                                uiState.blocks.find { it.id == target.block.id }
                                    ?: target.block
                            with(sts) {
                                Surface(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .sharedBounds(
                                                rememberSharedContentState("block_surface_${liveBlock.id}"),
                                                animatedVisibilityScope = avs,
                                            ),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    BlockContentInner(
                                        block = liveBlock,
                                        onBlockFocused = uiState.onBlockFocused,
                                        onBlockUpdated = uiState.onUpdateBlock,
                                        onBlockDeleted = uiState.onDeleteBlock,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        EditorDisplay.List -> {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets.ime),
                            ) {
                                LazyColumn(state = listState) {
                                    items(
                                        items = uiState.blocks,
                                        key = { block -> block.id },
                                    ) { block ->
                                        with(sts) {
                                            EntryEditorSurface(
                                                modifier =
                                                    Modifier
                                                        .animateItem()
                                                        .sharedBounds(
                                                            rememberSharedContentState("block_surface_${block.id}"),
                                                            animatedVisibilityScope = avs,
                                                        ),
                                            ) {
                                                BlockContentInner(
                                                    block = block,
                                                    onBlockFocused = uiState.onBlockFocused,
                                                    onBlockUpdated = uiState.onUpdateBlock,
                                                    onBlockDeleted = uiState.onDeleteBlock,
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        EditorContentFooter(
                                            scrollState = listState,
                                            onAddBlock = { type, id ->
                                                uiState.onCreateBlock(type, id)
                                                scope.launch { listState.scrollToEnd() }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BlockContentInner(
    block: EntryBlockUiState,
    onBlockFocused: (Uuid) -> Unit,
    onBlockUpdated: (EntryBlockUiState) -> Unit,
    onBlockDeleted: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is TextBlockUiState ->
            TextBlockContent(
                block = block,
                isExpanded = true,
                onTextChanged = { newText -> onBlockUpdated(block.copy(content = newText)) },
                onFocused = { onBlockFocused(block.id) },
                modifier = modifier,
            )

        is ImageBlockUiState ->
            app.logdate.feature.editor.ui.image.ImageBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                modifier = modifier,
            )

        is AudioBlockUiState ->
            AudioBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                modifier = modifier,
            )

        is CameraBlockUiState ->
            CameraBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                modifier = modifier,
            )

        is VideoBlockUiState ->
            VideoBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                modifier = modifier,
            )
    }
}
