package app.logdate.feature.postcards.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.ShapeKind
import app.logdate.ui.common.CursorType
import app.logdate.ui.common.cursorIcon
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen immersive canvas editor for creating and editing Postcards.
 *
 * Adapts layout by screen size:
 * - **Compact (phones):** Vertical layout — canvas, shelf, bottom tool bar
 * - **Medium+ (tablets, landscape):** Horizontal layout — side tool rail, canvas, shelf panel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(
    viewModel: CanvasEditorViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saveError) {
        state.saveError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearSaveError()
        }
    }

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideLayout = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar(
                title = state.document.title,
                onTitleChange = viewModel::setTitle,
                canUndo = viewModel.canUndo,
                canRedo = viewModel.canRedo,
                hasSelection = state.selectedElementId != null,
                onBack = onNavigateBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDelete = viewModel::deleteSelectedElement,
                onSave = {
                    viewModel.save()
                    onSaved()
                },
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { paddingValues ->
        val viewportState = rememberCanvasViewportState()

        val keyboardModifier =
            Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isModifier = event.isCtrlPressed || event.isMetaPressed
                when {
                    isModifier && !event.isShiftPressed && event.key == Key.Z -> {
                        viewModel.undo()
                        true
                    }
                    isModifier && event.isShiftPressed && event.key == Key.Z -> {
                        viewModel.redo()
                        true
                    }
                    event.key == Key.Delete || event.key == Key.Backspace -> {
                        if (state.selectedElementId != null) {
                            viewModel.deleteSelectedElement()
                            true
                        } else {
                            false
                        }
                    }
                    event.key == Key.Escape -> {
                        if (state.isTextEditorVisible) {
                            viewModel.cancelTextEditing()
                        } else {
                            viewModel.selectElement(null)
                        }
                        true
                    }
                    event.key == Key.One -> {
                        viewModel.setActiveTool(CanvasTool.SELECT)
                        true
                    }
                    event.key == Key.Two -> {
                        viewModel.setActiveTool(CanvasTool.INK)
                        true
                    }
                    event.key == Key.Three -> {
                        viewModel.setActiveTool(CanvasTool.SHAPE)
                        true
                    }
                    event.key == Key.Four -> {
                        viewModel.setActiveTool(CanvasTool.TEXT)
                        true
                    }
                    event.key == Key.Five -> {
                        viewModel.setActiveTool(CanvasTool.STICKER)
                        true
                    }
                    else -> false
                }
            }

        if (useWideLayout) {
            // Tablet / landscape: tool rail on left, canvas center, shelf on right
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .then(keyboardModifier),
            ) {
                ToolRail(
                    activeTool = state.activeTool,
                    onToolSelected = viewModel::setActiveTool,
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                )

                CanvasArea(
                    state = state,
                    viewModel = viewModel,
                    viewportState = viewportState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                Shelf(
                    mode = state.shelfMode,
                    photos = state.shelfPhotos,
                    browsePhotos = state.browsePhotos,
                    stickers = state.shelfStickers,
                    onModeChange = viewModel::setShelfMode,
                    onPhotoDrag = { photo, x, y -> viewModel.addPhotoElement(photo, x, y) },
                    onStickerTap = { sticker -> viewModel.addStickerElement(sticker.id, 0f, 0f) },
                    modifier =
                        Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                )
            }
        } else {
            // Phone: canvas on top, shelf + tool bar at bottom
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .then(keyboardModifier),
            ) {
                CanvasArea(
                    state = state,
                    viewModel = viewModel,
                    viewportState = viewportState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                // Text editor slides up over the shelf when active
                AnimatedVisibility(
                    visible = state.isTextEditorVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    val editingElement =
                        state.editingTextElementId?.let { id ->
                            state.document.elements.find { it.id == id } as? CanvasElement.Text
                        }
                    TextElementEditor(
                        initialText = editingElement?.content ?: "",
                        initialFont = editingElement?.fontFamily ?: FontChoice.CAVEAT.id,
                        initialColor = editingElement?.color ?: DEFAULT_STROKE_COLOR,
                        initialFontSize = editingElement?.fontSize ?: 24f,
                        onConfirm = { content, fontFamily, color, fontSize ->
                            val centerX = -viewportState.offset.x / viewportState.scale
                            val centerY = -viewportState.offset.y / viewportState.scale
                            viewModel.confirmTextEditing(content, fontFamily, color, fontSize, centerX, centerY)
                        },
                        onDismiss = viewModel::cancelTextEditing,
                    )
                }

                Shelf(
                    mode = state.shelfMode,
                    photos = state.shelfPhotos,
                    browsePhotos = state.browsePhotos,
                    stickers = state.shelfStickers,
                    onModeChange = viewModel::setShelfMode,
                    onPhotoDrag = { photo, x, y -> viewModel.addPhotoElement(photo, x, y) },
                    onStickerTap = { sticker -> viewModel.addStickerElement(sticker.id, 0f, 0f) },
                )

                ToolPalette(
                    activeTool = state.activeTool,
                    onToolSelected = viewModel::setActiveTool,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
    }
}

/**
 * The main canvas area with viewport, renderer, and tool overlays.
 */
@Composable
private fun CanvasArea(
    state: CanvasEditorState,
    viewModel: CanvasEditorViewModel,
    viewportState: CanvasViewportState,
    modifier: Modifier = Modifier,
) {
    val cursorType =
        when (state.activeTool) {
            CanvasTool.SELECT -> if (state.selectedElementId != null) CursorType.MOVE else CursorType.DEFAULT
            CanvasTool.INK, CanvasTool.SHAPE -> CursorType.CROSSHAIR
            CanvasTool.TEXT -> CursorType.TEXT
            CanvasTool.STICKER -> CursorType.DEFAULT
        }

    Box(modifier = modifier.cursorIcon(cursorType)) {
        val viewportGesturesEnabled =
            state.activeTool != CanvasTool.INK && state.activeTool != CanvasTool.SHAPE

        CanvasViewport(
            state = viewportState,
            gestureEnabled = viewportGesturesEnabled,
        ) {
            CanvasRenderer(
                document = state.document,
                selectedElementId = state.selectedElementId,
                stickerUriMap = state.stickerUriMap,
                viewportOffsetX = viewportState.offset.x,
                viewportOffsetY = viewportState.offset.y,
                onElementTap = { elementId ->
                    val element = state.document.elements.find { it.id == elementId }
                    if (element is CanvasElement.Text) {
                        viewModel.startTextEditing(elementId)
                    } else {
                        viewModel.selectElement(elementId)
                    }
                },
            )
        }

        // Tool overlays with animated crossfade
        AnimatedContent(
            targetState = state.activeTool,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tool-overlay",
        ) { tool ->
            when (tool) {
                CanvasTool.SELECT -> {
                    val selectedId = state.selectedElementId
                    if (selectedId != null) {
                        SelectionOverlay(
                            selectedElementId = selectedId,
                            viewportScale = viewportState.scale,
                            onBeginDrag = viewModel::beginDrag,
                            onMoveElement = viewModel::moveElement,
                            onEndDrag = viewModel::endDrag,
                            onTransformElement = viewModel::transformElement,
                            onDeselect = { viewModel.selectElement(null) },
                        )
                    } else {
                        Box(Modifier)
                    }
                }
                CanvasTool.INK -> {
                    InkCaptureOverlay(
                        tool = InkTool.PEN,
                        color = DEFAULT_STROKE_COLOR,
                        strokeWidth = DEFAULT_INK_WIDTH,
                        onStrokeComplete = { points ->
                            viewModel.addInkStroke(
                                points = points,
                                tool = InkTool.PEN,
                                color = DEFAULT_STROKE_COLOR,
                                strokeWidth = DEFAULT_INK_WIDTH,
                            )
                        },
                    )
                }
                CanvasTool.SHAPE -> {
                    ShapeCaptureOverlay(
                        shapeKind = ShapeKind.RECTANGLE,
                        color = DEFAULT_STROKE_COLOR,
                        strokeWidth = DEFAULT_SHAPE_WIDTH,
                        onShapeComplete = { draft ->
                            viewModel.addShape(
                                shapeKind = draft.kind,
                                x = draft.x,
                                y = draft.y,
                                width = draft.width,
                                height = draft.height,
                                color = DEFAULT_STROKE_COLOR,
                                strokeWidth = DEFAULT_SHAPE_WIDTH,
                            )
                        },
                    )
                }
                CanvasTool.TEXT -> {
                    if (!state.isTextEditorVisible) {
                        viewModel.startTextEditing()
                    }
                    Box(Modifier)
                }
                CanvasTool.STICKER -> {
                    viewModel.setShelfMode(ShelfMode.Stickers)
                    Box(Modifier)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    title: String,
    onTitleChange: (String) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var editableTitle by remember(title) { mutableStateOf(title) }

    TopAppBar(
        modifier = modifier,
        title = {
            if (isEditingTitle) {
                BasicTextField(
                    value = editableTitle,
                    onValueChange = { editableTitle = it },
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    decorationBox = { innerTextField ->
                        if (editableTitle.isEmpty()) {
                            Text(
                                "Untitled",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            } else {
                Text(
                    text = title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (title.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.clickable { isEditingTitle = true },
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Undo (Ctrl+Z)") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Redo (Ctrl+Shift+Z)") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
            }
            if (hasSelection) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Delete (Del)") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Save") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = {
                    if (isEditingTitle) {
                        onTitleChange(editableTitle)
                        isEditingTitle = false
                    }
                    onSave()
                }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save")
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ),
    )
}

// --- Shelf ---

@Composable
private fun Shelf(
    mode: ShelfMode,
    photos: List<ShelfPhoto>,
    browsePhotos: List<ShelfPhoto>,
    stickers: List<StickerShelfItem>,
    onModeChange: (ShelfMode) -> Unit,
    onPhotoDrag: (ShelfPhoto, Float, Float) -> Unit,
    onStickerTap: (StickerShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode is ShelfMode.Photos,
                onClick = { onModeChange(ShelfMode.Photos) },
                label = { Text("Photos") },
            )
            FilterChip(
                selected = mode is ShelfMode.Stickers,
                onClick = { onModeChange(ShelfMode.Stickers) },
                label = { Text("Stickers") },
            )
            FilterChip(
                selected = mode is ShelfMode.Browse,
                onClick = { onModeChange(ShelfMode.Browse) },
                label = { Text("Browse") },
            )
        }

        when (mode) {
            is ShelfMode.Photos -> ShelfPhotoStrip(photos, onPhotoDrag)
            is ShelfMode.Stickers -> StickerShelfStrip(stickers, onStickerTap)
            is ShelfMode.Browse -> ShelfPhotoStrip(browsePhotos, onPhotoDrag)
        }
    }
}

@Composable
private fun ShelfPhotoStrip(
    photos: List<ShelfPhoto>,
    onPhotoDrag: (ShelfPhoto, Float, Float) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Your photos will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.mediaUri }) { photo ->
            AsyncImage(
                model = photo.mediaUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoDrag(photo, 0f, 0f) },
            )
        }
    }
}

@Composable
private fun StickerShelfStrip(
    stickers: List<StickerShelfItem>,
    onStickerTap: (StickerShelfItem) -> Unit,
) {
    if (stickers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Extract stickers from photos in your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(stickers, key = { it.id.toString() }) { sticker ->
            AsyncImage(
                model = sticker.imageUri,
                contentDescription = sticker.label,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onStickerTap(sticker) },
            )
        }
    }
}

// --- Tool Palette (compact - bottom bar) ---

@Composable
private fun ToolPalette(
    activeTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tool in CanvasTool.entries) {
            ToolButton(
                icon = tool.icon,
                label = tool.label,
                isActive = activeTool == tool,
                onClick = { onToolSelected(tool) },
                shortcut = tool.shortcut,
            )
        }
    }
}

// --- Tool Rail (expanded - side bar) ---

@Composable
private fun ToolRail(
    activeTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tool in CanvasTool.entries) {
            ToolButton(
                icon = tool.icon,
                label = tool.label,
                isActive = activeTool == tool,
                onClick = { onToolSelected(tool) },
                shortcut = tool.shortcut,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
) {
    val tint =
        if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val tooltipText = if (shortcut != null) "$label ($shortcut)" else label

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clickable(onClick = onClick),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

private val CanvasTool.icon: ImageVector
    get() =
        when (this) {
            CanvasTool.SELECT -> Icons.Filled.NearMe
            CanvasTool.INK -> Icons.Filled.Create
            CanvasTool.SHAPE -> Icons.Filled.CropSquare
            CanvasTool.TEXT -> Icons.Filled.TextFields
            CanvasTool.STICKER -> Icons.Filled.Circle
        }

private val CanvasTool.label: String
    get() =
        when (this) {
            CanvasTool.SELECT -> "Select"
            CanvasTool.INK -> "Ink"
            CanvasTool.SHAPE -> "Shape"
            CanvasTool.TEXT -> "Text"
            CanvasTool.STICKER -> "Sticker"
        }

private val CanvasTool.shortcut: String
    get() =
        when (this) {
            CanvasTool.SELECT -> "1"
            CanvasTool.INK -> "2"
            CanvasTool.SHAPE -> "3"
            CanvasTool.TEXT -> "4"
            CanvasTool.STICKER -> "5"
        }

private const val DEFAULT_STROKE_COLOR = "#333333"
private const val DEFAULT_INK_WIDTH = 4f
private const val DEFAULT_SHAPE_WIDTH = 3f
