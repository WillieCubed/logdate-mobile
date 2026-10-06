@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.journals.ui.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.PanelHeader
import app.logdate.ui.workspace.WorkspacePanel
import app.logdate.ui.workspace.WorkspaceSearchField
import app.logdate.ui.workspace.WorkspaceSearchScope
import app.logdate.util.formatDateLocalized
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import androidx.compose.foundation.lazy.items as lazyItems

@Composable
fun JournalContentPickerScreen(
    journalId: Uuid,
    onBack: () -> Unit,
    onAdded: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    viewModel: JournalContentPickerViewModel = koinViewModel(),
) {
    LaunchedEffect(journalId) { viewModel.setJournalId(journalId) }
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.addedCount) {
        if (state.addedCount != null) onAdded()
    }
    JournalContentPickerScreenContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::updateSearchQuery,
        onToggleSelection = viewModel::toggleSelection,
        onRemoveSelection = viewModel::removeSelection,
        onAddSelected = viewModel::addSelected,
        modifier = modifier,
    )
}

@Composable
fun JournalContentPickerScreenContent(
    state: JournalContentPickerUiState,
    onBack: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onToggleSelection: (Uuid) -> Unit = {},
    onRemoveSelection: (Uuid) -> Unit = {},
    onAddSelected: () -> Unit = {},
    previewMedia: Map<Uuid, Painter> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val workspaceEnabled = LocalWorkspaceEnabled.current
    if (workspaceEnabled) {
        WorkspaceSearchScope(
            query = state.query,
            hint = "Search content",
            onQuery = onQueryChange,
        )
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        WorkspacePanel(
            modifier = modifier.padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (workspaceEnabled) {
                    PanelHeader(
                        title = if (state.journalTitle.isBlank()) "Add existing content" else "Add to ${state.journalTitle}",
                        subtitle = "Pick anything already in your LogDate. It stays where it is.",
                        actions = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to journal")
                            }
                        },
                    )
                }
                if (!workspaceEnabled) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                        WorkspaceSearchField(
                            query = state.query,
                            hint = "Search content",
                            onQueryChange = onQueryChange,
                            modifier = Modifier.semantics { contentDescription = "Search existing content" },
                        )
                    }
                }
                PickerGallery(
                    state = state,
                    onToggleSelection = onToggleSelection,
                    previewMedia = previewMedia,
                    modifier = Modifier.weight(1f),
                )
                SelectionReview(
                    selectedItems = state.selectedItems,
                    isAdding = state.isAdding,
                    error = state.addError,
                    onRemoveSelection = onRemoveSelection,
                    onAddSelected = onAddSelected,
                )
            }
        }
    }

    if (workspaceEnabled) {
        body(PaddingValues())
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(if (state.journalTitle.isBlank()) "Add existing content" else "Add to ${state.journalTitle}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to journal")
                        }
                    },
                )
            },
            content = body,
        )
    }
}

@Composable
private fun PickerGallery(
    state: JournalContentPickerUiState,
    onToggleSelection: (Uuid) -> Unit,
    previewMedia: Map<Uuid, Painter>,
    modifier: Modifier = Modifier,
) {
    val selectedIds = state.selectedItems.mapTo(mutableSetOf()) { it.id }
    if (state.groups.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.query.isBlank()) "No content available to add." else "No matching content.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridColumns = contentPickerGridColumnCount(maxWidth, LocalDensity.current.fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = formatDateLocalized(group.date),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                    )
                }
                items(
                    items = group.items,
                    key = { it.id },
                    span = { item ->
                        GridItemSpan(contentPickerItemSpan(item, gridColumns))
                    },
                ) { item ->
                    PickerItemTile(
                        item = item,
                        selected = item.id in selectedIds,
                        previewMedia = previewMedia[item.id],
                        onClick = { onToggleSelection(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerItemTile(
    item: JournalContentPickerItem,
    selected: Boolean,
    previewMedia: Painter?,
    onClick: () -> Unit,
) {
    val typeLabel = item.kind.label
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Checkbox
                    this.selected = selected
                    contentDescription = "$typeLabel: ${item.title}. ${if (selected) "Selected" else "Not selected"}"
                }.clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected && !item.isVisualMedia) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                contentColor =
                    if (selected && !item.isVisualMedia) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        if (item.isVisualMedia) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            if (item.kind == JournalContentPickerItemKind.VIDEO) {
                                Icons.Rounded.PlayCircle
                            } else {
                                Icons.Rounded.Image
                            },
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    previewMedia != null ->
                        Image(
                            painter = previewMedia,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    item.mediaRef != null ->
                        AsyncImage(
                            model = item.mediaRef,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                }
                if (item.kind == JournalContentPickerItemKind.VIDEO) {
                    Icon(
                        Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                SelectionMarker(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                )
            }
        } else if (item.kind == JournalContentPickerItemKind.RECORDING) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SelectionMarker(selected, Modifier.align(Alignment.TopEnd))
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 36.dp).widthIn(max = 720.dp),
                )
                SelectionMarker(selected, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

internal fun contentPickerGridColumnCount(
    availableWidth: Dp,
    fontScale: Float,
): Int {
    if (fontScale >= 1.5f) return 1
    val usableWidth = (availableWidth - Spacing.lg * 2).coerceAtLeast(0.dp)
    return (usableWidth / 176.dp).toInt().coerceIn(1, 6)
}

internal fun contentPickerItemSpan(
    item: JournalContentPickerItem,
    gridColumns: Int,
): Int =
    when (item.kind) {
        JournalContentPickerItemKind.WRITING -> ((gridColumns + 1) / 2).coerceAtLeast(2).coerceAtMost(gridColumns)
        JournalContentPickerItemKind.RECORDING -> if (gridColumns >= 6) 2 else 1
        JournalContentPickerItemKind.PHOTO,
        JournalContentPickerItemKind.VIDEO,
        -> 1
    }

@Composable
private fun SelectionMarker(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.padding(4.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private val JournalContentPickerItem.isVisualMedia: Boolean
    get() = kind == JournalContentPickerItemKind.PHOTO || kind == JournalContentPickerItemKind.VIDEO

private val JournalContentPickerItemKind.label: String
    get() =
        when (this) {
            JournalContentPickerItemKind.WRITING -> "Writing"
            JournalContentPickerItemKind.PHOTO -> "Photo"
            JournalContentPickerItemKind.VIDEO -> "Video"
            JournalContentPickerItemKind.RECORDING -> "Recording"
        }

@Composable
private fun SelectionReview(
    selectedItems: List<JournalContentPickerItem>,
    isAdding: Boolean,
    error: String?,
    onRemoveSelection: (Uuid) -> Unit,
    onAddSelected: () -> Unit,
) {
    if (selectedItems.isEmpty() && error == null) return
    Surface(tonalElevation = 3.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (selectedItems.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        lazyItems(selectedItems, key = { it.id }) { item ->
                            Card(Modifier.widthIn(max = 168.dp)) {
                                Row(
                                    modifier = Modifier.padding(start = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onRemoveSelection(item.id) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${item.title} from selection")
                                    }
                                }
                            }
                        }
                    }
                }
                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onAddSelected) { Text("Retry") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    Text(
                        text = "${selectedItems.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onAddSelected, enabled = selectedItems.isNotEmpty() && !isAdding) {
                        Text(if (isAdding) "Adding…" else "Add ${selectedItems.size}")
                    }
                }
            }
        }
    }
}
