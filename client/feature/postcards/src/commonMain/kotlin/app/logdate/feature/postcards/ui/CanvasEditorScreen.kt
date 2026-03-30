package app.logdate.feature.postcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.ShapeKind
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen immersive canvas editor for creating and editing Postcards.
 *
 * The editor has three main zones:
 * 1. **Canvas** — the pannable/zoomable editing area
 * 2. **Shelf** — content staging tray at the bottom (photos, stickers, browse)
 * 3. **Tool palette** — tool selection bar at the very bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(
    viewModel: CanvasEditorViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            EditorTopBar(
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
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Canvas area
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                val viewportState = rememberCanvasViewportState()
                CanvasViewport(
                    state = viewportState,
                ) {
                    CanvasRenderer(
                        document = state.document,
                        selectedElementId = state.selectedElementId,
                        stickerUriMap = state.stickerUriMap,
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

                // Tool overlays — rendered on top of the viewport
                when (state.activeTool) {
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
                    }
                    else -> { /* STICKER handled in later phases */ }
                }
            }

            // Text editor overlay — shown above shelf when active
            if (state.isTextEditorVisible) {
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
                        viewModel.confirmTextEditing(content, fontFamily, color, fontSize)
                    },
                    onDismiss = viewModel::cancelTextEditing,
                )
            }

            // Shelf
            Shelf(
                mode = state.shelfMode,
                photos = state.shelfPhotos,
                browsePhotos = state.browsePhotos,
                stickers = state.shelfStickers,
                onModeChange = viewModel::setShelfMode,
                onPhotoDrag = { photo, x, y ->
                    viewModel.addPhotoElement(photo, x, y)
                },
                onStickerTap = { sticker ->
                    viewModel.addStickerElement(sticker.id, 0f, 0f)
                },
            )

            // Tool palette
            ToolPalette(
                activeTool = state.activeTool,
                onToolSelected = viewModel::setActiveTool,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            if (hasSelection) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Filled.Check, contentDescription = "Save")
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
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
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Mode tabs
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

        // Content strip
        when (mode) {
            is ShelfMode.Photos -> {
                ShelfPhotoStrip(
                    photos = photos,
                    onPhotoDrag = onPhotoDrag,
                )
            }
            is ShelfMode.Stickers -> {
                StickerShelfStrip(
                    stickers = stickers,
                    onStickerTap = onStickerTap,
                )
            }
            is ShelfMode.Browse -> {
                ShelfPhotoStrip(
                    photos = browsePhotos,
                    onPhotoDrag = onPhotoDrag,
                )
            }
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No photos available",
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
            ShelfPhotoItem(
                photo = photo,
                onTap = {
                    // For now, tap places at canvas center. Full drag gesture comes with
                    // the transform overlay in Phase 3 refinement.
                    onPhotoDrag(photo, 0f, 0f)
                },
            )
        }
    }
}

@Composable
private fun ShelfPhotoItem(
    photo: ShelfPhoto,
    onTap: () -> Unit,
) {
    AsyncImage(
        model = photo.mediaUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTap),
    )
}

@Composable
private fun StickerShelfStrip(
    stickers: List<StickerShelfItem>,
    onStickerTap: (StickerShelfItem) -> Unit,
) {
    if (stickers.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No stickers yet",
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

// --- Tool Palette ---

@Composable
private fun ToolPalette(
    activeTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolButton(
            icon = Icons.Filled.NearMe,
            label = "Select",
            isActive = activeTool == CanvasTool.SELECT,
            onClick = { onToolSelected(CanvasTool.SELECT) },
        )
        ToolButton(
            icon = Icons.Filled.Create,
            label = "Ink",
            isActive = activeTool == CanvasTool.INK,
            onClick = { onToolSelected(CanvasTool.INK) },
        )
        ToolButton(
            icon = Icons.Filled.CropSquare,
            label = "Shape",
            isActive = activeTool == CanvasTool.SHAPE,
            onClick = { onToolSelected(CanvasTool.SHAPE) },
        )
        ToolButton(
            icon = Icons.Filled.TextFields,
            label = "Text",
            isActive = activeTool == CanvasTool.TEXT,
            onClick = { onToolSelected(CanvasTool.TEXT) },
        )
        ToolButton(
            icon = Icons.Filled.Circle,
            label = "Sticker",
            isActive = activeTool == CanvasTool.STICKER,
            onClick = { onToolSelected(CanvasTool.STICKER) },
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val tint =
        if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
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

private const val DEFAULT_STROKE_COLOR = "#333333"
private const val DEFAULT_INK_WIDTH = 4f
private const val DEFAULT_SHAPE_WIDTH = 3f
