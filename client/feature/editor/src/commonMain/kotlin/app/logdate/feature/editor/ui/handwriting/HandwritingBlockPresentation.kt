package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.state.EditorBlockPresentation
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.ink_preview_description
import logdate.client.feature.editor.generated.resources.ink_read_preview_description
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/** The same paper/ink morph used by the prototype, attached to real editor-owned blocks. */
@Composable
internal fun rememberHandwritingBlockPresentations(
    state: EditorState,
    onUpdateBlock: (EntryBlockUiState) -> Unit,
    focusProgress: Float,
    contextAvailable: Boolean,
    adaptToTheme: Boolean = false,
    keepInkBelowControls: Boolean = false,
): Map<Uuid, EditorBlockPresentation> =
    buildMap {
        state.blocks.filterIsInstance<HandwritingBlockUiState>().forEach { block ->
            key(block.id) {
                var history by remember { mutableStateOf(HandwritingHistory(block.page)) }
                val tools = remember { HandwritingDrawingSettings() }
                LaunchedEffect(block.page) {
                    if (block.page != history.page) history = HandwritingHistory(block.page)
                }
                val onHistoryChanged: (HandwritingHistory) -> Unit = { next ->
                    if (!state.isReadOnly(block.id) && !state.isEditingLocked) {
                        history = next
                        onUpdateBlock(block.copy(page = next.page))
                    }
                }
                val inlinePaper = if (adaptToTheme) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                val focusedPaper = if (adaptToTheme) inlinePaper else Color.White
                put(
                    block.id,
                    EditorBlockPresentation(
                        immersive = true,
                        focusTransitionProgress = focusProgress,
                        inline = { open ->
                            Box(Modifier.padding(16.dp)) {
                                InlineHandwritingBlock(
                                    block.id,
                                    history.page,
                                    tools,
                                    focusProgress,
                                    description =
                                        stringResource(
                                            if (state.isReadOnly(
                                                    block.id,
                                                )
                                            ) {
                                                Res.string.ink_read_preview_description
                                            } else {
                                                Res.string.ink_preview_description
                                            },
                                        ),
                                    onFocus = open,
                                    paperColor = lerp(inlinePaper, focusedPaper, focusProgress),
                                    adaptInkToPaper = adaptToTheme,
                                    fitPreviewToWidth = true,
                                    minPreviewHeight = 208.dp,
                                    shareCanvas = false,
                                )
                            }
                        },
                        focused = {
                            FocusedHandwritingBlock(
                                block.id,
                                history,
                                tools,
                                context = state.blocks.filter { it.id != block.id },
                                onEditHistory = { edit -> onHistoryChanged(edit(history)) },
                                focusProgress = focusProgress,
                                contextAvailable = contextAvailable && rememberFoldableLayoutInfo().hinge?.isSeparating != true,
                                editable = !state.isReadOnly(block.id) && !state.isEditingLocked,
                                paperColor = lerp(inlinePaper, focusedPaper, focusProgress),
                                adaptInkToPaper = adaptToTheme,
                                keepInkBelowControls = keepInkBelowControls,
                                fitPreviewToWidth = true,
                                shareSurface = true,
                            )
                        },
                    ),
                )
            }
        }
    }
