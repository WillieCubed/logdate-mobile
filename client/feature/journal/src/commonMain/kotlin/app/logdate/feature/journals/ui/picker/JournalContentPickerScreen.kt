@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.journals.ui.picker

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.PanelHeader
import app.logdate.ui.workspace.WorkspacePanel
import app.logdate.ui.workspace.WorkspaceSearchScope
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
    modifier: Modifier = Modifier,
) {
    val workspaceEnabled = LocalWorkspaceEnabled.current
    if (workspaceEnabled) {
        WorkspaceSearchScope(
            query = state.query,
            hint = "Search writing, captions, and recordings",
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
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        singleLine = true,
                        label = { Text("Search existing content") },
                    )
                }
                PickerGallery(
                    state = state,
                    onToggleSelection = onToggleSelection,
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(group.date.toString(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md))
            }
            items(group.items, key = { it.id }) { item ->
                PickerItemTile(
                    item = item,
                    selected = item.id in selectedIds,
                    onClick = { onToggleSelection(item.id) },
                )
            }
        }
    }
}

@Composable
private fun PickerItemTile(
    item: JournalContentPickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val typeLabel =
        when (item.kind) {
            JournalContentPickerItemKind.WRITING -> "Writing"
            JournalContentPickerItemKind.PHOTO -> "Photo"
            JournalContentPickerItemKind.VIDEO -> "Video"
            JournalContentPickerItemKind.RECORDING -> "Recording"
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Checkbox
                    this.selected = selected
                    contentDescription = "$typeLabel: ${item.title}. ${if (selected) "Selected" else "Not selected"}"
                }.clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            val showsMediaThumbnail =
                item.mediaRef != null &&
                    (item.kind == JournalContentPickerItemKind.PHOTO || item.kind == JournalContentPickerItemKind.VIDEO)
            if (showsMediaThumbnail) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AsyncImage(
                    model = item.mediaRef,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        if (item.kind ==
                            JournalContentPickerItemKind.RECORDING
                        ) {
                            Icons.Rounded.GraphicEq
                        } else {
                            Icons.AutoMirrored.Rounded.Article
                        },
                        contentDescription = null,
                    )
                    Text(item.title, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (item.kind == JournalContentPickerItemKind.VIDEO) {
                Icon(
                    Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
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
    }
}

@Composable
private fun SelectionReview(
    selectedItems: List<JournalContentPickerItem>,
    isAdding: Boolean,
    error: String?,
    onRemoveSelection: (Uuid) -> Unit,
    onAddSelected: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (selectedItems.isNotEmpty()) {
                Text("Selected (${selectedItems.size})", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    lazyItems(selectedItems, key = { it.id }) { item ->
                        Card(Modifier.widthIn(max = 180.dp)) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAddSelected, enabled = selectedItems.isNotEmpty() && !isAdding) {
                    Text(if (isAdding) "Adding…" else "Add ${selectedItems.size}")
                }
            }
        }
    }
}
