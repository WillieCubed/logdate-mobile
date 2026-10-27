@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope
import app.logdate.feature.editor.ui.LocalBlockFocusProgress
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.blocks.EntryContextPreview
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.layout.EditorBlockCornerRadius
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkTool
import kotlinx.coroutines.flow.first
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.ink_canvas_description
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

internal class HandwritingDrawingSettings {
    val pageScroll = ScrollState(0)
    var controlsHeight by mutableStateOf(0.dp)
    var topControlsHeight by mutableStateOf(0.dp)
    var scrollPositioned by mutableStateOf(false)
    var tool by mutableStateOf(InkTool.PEN)
    var color by mutableStateOf("#263044")
    var width by mutableStateOf(3.5f)
    var allowTouch by mutableStateOf(false)
    var ruled by mutableStateOf(true)
    var showContext by mutableStateOf(false)
}

/** Camera-like drawing: the page reaches the editor edges; only drawing controls float above it. */
@Composable
internal fun FocusedHandwritingBlock(
    blockId: Uuid,
    history: HandwritingHistory,
    tools: HandwritingDrawingSettings,
    context: List<EntryBlockUiState>,
    onEditHistory: ((HandwritingHistory) -> HandwritingHistory) -> Unit,
    focusProgress: Float = 1f,
    contextAvailable: Boolean,
    editable: Boolean = true,
    paperColor: Color = Color.White,
    fitPreviewToWidth: Boolean = false,
    adaptInkToPaper: Boolean = false,
    keepInkBelowControls: Boolean = false,
    shareSurface: Boolean = false,
) {
    val density = LocalDensity.current
    val transitioning = LocalSharedTransitionScope.current?.isTransitionActive == true
    val visibility = LocalAnimatedVisibilityScope.current?.transition
    val settled =
        !transitioning &&
            (visibility == null || (visibility.currentState == EnterExitState.Visible && visibility.targetState == EnterExitState.Visible))
    val paperProgress = LocalBlockFocusProgress.current ?: focusProgress
    val controlsProgress by animateFloatAsState(
        if (settled) 1f else 0f,
        tween(140),
        label = "handwritingTools",
    )
    val controlsModifier =
        Modifier.graphicsLayer {
            alpha = controlsProgress
            translationY = (1f - controlsProgress) * 12.dp.toPx()
        }
    LaunchedEffect(tools.topControlsHeight) {
        if (!tools.scrollPositioned && tools.topControlsHeight > 0.dp) {
            val initialOffset = with(density) { tools.topControlsHeight.roundToPx() }
            snapshotFlow { tools.pageScroll.maxValue }.first { it >= initialOffset }
            tools.pageScroll.scrollTo(if (keepInkBelowControls) 0 else initialOffset)
            tools.scrollPositioned = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (tools.showContext && contextAvailable && paperProgress > 0f) {
                Box(Modifier.width(300.dp * paperProgress).clipToBounds()) {
                    EntryContextPreview(
                        context,
                        Modifier.requiredWidth(300.dp).padding(top = 80.dp).graphicsLayer { alpha = paperProgress },
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(if (shareSurface) Modifier.handwritingSharedElement(blockId, paperProgress) else Modifier)
                    .clip(RoundedCornerShape(EditorBlockCornerRadius * (1f - paperProgress)))
                    .background(paperColor)
                    .testTag("handwriting_drawing_area"),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val viewportHeight = with(density) { maxHeight.toPx() }
                    val pageHeight = maxOf(maxHeight, maxWidth * (history.page.height / history.page.width))
                    Column(
                        Modifier.fillMaxSize().verticalScroll(tools.pageScroll).padding(
                            top = tools.topControlsHeight,
                            bottom =
                                tools.controlsHeight + 8.dp,
                        ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(if (shareSurface) Modifier else Modifier.handwritingSharedElement(blockId, paperProgress))
                                .height(pageHeight)
                                .testTag("handwriting_focus_surface"),
                        ) {
                            HandwritingCanvas(
                                history.page,
                                Modifier.fillMaxSize(),
                                inputEnabled = true,
                                paperColor = paperColor,
                                fitPreviewToWidth = fitPreviewToWidth,
                                adaptInkToPaper = adaptInkToPaper,
                                captureEnabled = editable && settled,
                                presentationProgress = paperProgress,
                                previewViewportHeight = viewportHeight,
                                allowTouch = tools.allowTouch,
                                tool = tools.tool,
                                inkColor = tools.color,
                                strokeWidth = if (tools.tool == InkTool.HIGHLIGHTER) tools.width * 5f else tools.width,
                                ruled = tools.ruled,
                                description = stringResource(Res.string.ink_canvas_description),
                                onStroke = { points, activeTool ->
                                    onEditHistory { current ->
                                        val page = current.page
                                        val edited =
                                            if (activeTool == InkTool.ERASER) {
                                                page.erase(points, radius = 14f)
                                            } else {
                                                page.copy(
                                                    height = maxOf(page.height, (points.maxOfOrNull { it.y } ?: 0f) + 48f),
                                                    strokes =
                                                        page.strokes +
                                                            CanvasElement.Ink(
                                                                Uuid.random(),
                                                                activeTool,
                                                                tools.color,
                                                                if (activeTool == InkTool.HIGHLIGHTER) tools.width * 5f else tools.width,
                                                                points,
                                                            ),
                                                )
                                            }
                                        current.edit(edited)
                                    }
                                },
                            )
                        }
                    }
                }
                if (editable) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .onSizeChanged { tools.topControlsHeight = with(density) { it.height.toDp() } }
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .statusBarsPadding()
                            .padding(16.dp)
                            .then(controlsModifier),
                    ) {
                        HandwritingHistoryTools(
                            history,
                            onUndo = { onEditHistory { it.undo() } },
                            onRedo = { onEditHistory { it.redo() } },
                        )
                    }
                }
                if (editable) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged { tools.controlsHeight = with(density) { it.height.toDp() } }
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalEntryCaptureBarHeight.current)
                                .widthIn(max = 640.dp)
                                .testTag("handwriting_floating_tools")
                                .then(controlsModifier),
                    ) {
                        HandwritingTools(
                            tools.tool,
                            tools.color,
                            tools.width,
                            tools.allowTouch,
                            tools.ruled,
                            tools.showContext,
                            contextAvailable,
                            onTool = { tools.tool = it },
                            onColor = { tools.color = it },
                            onWidth = { tools.width = it },
                            onTouch = { tools.allowTouch = !tools.allowTouch },
                            onRules = { tools.ruled = !tools.ruled },
                            onContext = { tools.showContext = !tools.showContext },
                        )
                    }
                }
            }
        }
    }
}
