@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.journals.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.feature.journals.ui.deriveCoverColor
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.Spacing
import app.logdate.ui.workspace.AdaptiveWorkspaceLayout
import app.logdate.ui.workspace.LocalPanelLayoutInfo
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.LocalWorkspaceHosted
import app.logdate.ui.workspace.PanelConstraints
import app.logdate.ui.workspace.PanelHeader
import app.logdate.ui.workspace.WorkspacePanel
import app.logdate.ui.workspace.WorkspaceSearchField
import app.logdate.ui.workspace.WorkspaceSearchScope
import app.logdate.util.toReadableDateShort
import coil3.compose.AsyncImage
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_merge_back
import logdate.client.feature.journal.generated.resources.journal_merge_cancel
import logdate.client.feature.journal.generated.resources.journal_merge_change_destination
import logdate.client.feature.journal.generated.resources.journal_merge_changed
import logdate.client.feature.journal.generated.resources.journal_merge_clear_search
import logdate.client.feature.journal.generated.resources.journal_merge_combined
import logdate.client.feature.journal.generated.resources.journal_merge_confirm
import logdate.client.feature.journal.generated.resources.journal_merge_empty
import logdate.client.feature.journal.generated.resources.journal_merge_explanation
import logdate.client.feature.journal.generated.resources.journal_merge_failed
import logdate.client.feature.journal.generated.resources.journal_merge_from
import logdate.client.feature.journal.generated.resources.journal_merge_item_count
import logdate.client.feature.journal.generated.resources.journal_merge_keep
import logdate.client.feature.journal.generated.resources.journal_merge_loading
import logdate.client.feature.journal.generated.resources.journal_merge_merging
import logdate.client.feature.journal.generated.resources.journal_merge_no_matches
import logdate.client.feature.journal.generated.resources.journal_merge_overlap
import logdate.client.feature.journal.generated.resources.journal_merge_picker_subtitle
import logdate.client.feature.journal.generated.resources.journal_merge_picker_title
import logdate.client.feature.journal.generated.resources.journal_merge_recovery
import logdate.client.feature.journal.generated.resources.journal_merge_recovery_explanation
import logdate.client.feature.journal.generated.resources.journal_merge_review_title
import logdate.client.feature.journal.generated.resources.journal_merge_search
import logdate.client.feature.journal.generated.resources.journal_merge_unavailable
import logdate.client.feature.journal.generated.resources.journal_merge_updated
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
fun JournalMergeScreenContent(
    state: JournalMergeUiState,
    onBack: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onChoose: (Uuid) -> Unit = {},
    onChangeDestination: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val workspaceEnabled = LocalWorkspaceEnabled.current && LocalWorkspaceHosted.current
    val picker = state.stage == JournalMergeStage.Picker
    val merging = state.stage is JournalMergeStage.Merging
    val listState = rememberLazyListState()
    LaunchedEffect(state.error) {
        if (state.error == JournalMergeError.Changed) listState.scrollToItem(0)
    }
    val title =
        stringResource(if (picker) Res.string.journal_merge_picker_title else Res.string.journal_merge_review_title, state.sourceTitle)
    val searchHint = stringResource(Res.string.journal_merge_search)
    if (workspaceEnabled) {
        WorkspaceSearchScope(state.query, searchHint, onQueryChange, enabled = picker)
    }
    val body: @Composable (PaddingValues) -> Unit = { insets ->
        WorkspacePanel(
            modifier
                .fillMaxSize()
                .padding(insets)
                .testTag("journal_merge_panel")
                .semantics { paneTitle = title },
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (workspaceEnabled || picker) {
                    item {
                        PanelHeader(
                            title = title,
                            subtitle = if (picker) stringResource(Res.string.journal_merge_picker_subtitle) else null,
                            actions = {
                                if (workspaceEnabled) {
                                    IconButton(onClick = onBack, enabled = !merging) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.journal_merge_back))
                                    }
                                }
                            },
                        )
                    }
                }
                if (state.recovery && picker) {
                    item { MergeNotice(stringResource(Res.string.journal_merge_recovery)) }
                }
                state.error?.takeIf { picker }?.let { error ->
                    item {
                        MergeNotice(
                            stringResource(
                                when (error) {
                                    JournalMergeError.Failed -> Res.string.journal_merge_failed
                                    JournalMergeError.Changed -> Res.string.journal_merge_changed
                                    JournalMergeError.Unavailable -> Res.string.journal_merge_unavailable
                                },
                            ),
                            error = true,
                        )
                    }
                }
                if (picker) {
                    if (!workspaceEnabled) {
                        item {
                            WorkspaceSearchField(
                                query = state.query,
                                hint = searchHint,
                                onQueryChange = onQueryChange,
                                modifier = Modifier.padding(horizontal = Spacing.lg),
                            )
                        }
                    }
                    if (state.loading) {
                        item { MergeNotice(stringResource(Res.string.journal_merge_loading)) }
                    } else if (state.candidates.isEmpty()) {
                        item { MergeNotice(stringResource(Res.string.journal_merge_empty)) }
                    } else if (state.visibleCandidates.isEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = Spacing.lg)) {
                                Text(stringResource(Res.string.journal_merge_no_matches))
                                TextButton(onClick = { onQueryChange("") }) { Text(stringResource(Res.string.journal_merge_clear_search)) }
                            }
                        }
                    } else {
                        items(state.visibleCandidates, key = { it.journal.id }) { candidate ->
                            Surface(
                                onClick = { onChoose(candidate.journal.id) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                JournalMergeSummary(
                                    candidate.journal,
                                    pluralStringResource(Res.plurals.journal_merge_item_count, candidate.itemCount, candidate.itemCount),
                                    showUpdated = true,
                                )
                            }
                        }
                    }
                } else {
                    val preview =
                        when (val stage = state.stage) {
                            is JournalMergeStage.Review -> stage.preview
                            is JournalMergeStage.Merging -> stage.preview
                            else -> null
                        }
                    if (preview != null) {
                        item {
                            ReviewContent(preview, state.recovery, merging, state.error, onChangeDestination, onConfirm, onCancel)
                        }
                    }
                }
            }
        }
    }
    if (workspaceEnabled) {
        val panel = LocalPanelLayoutInfo.current
        if (panel == null || panel.width > 560.dp) {
            AdaptiveWorkspaceLayout(modifier, focusConstraints = PanelConstraints.ReadingCollection) { body(PaddingValues()) }
        } else {
            body(PaddingValues())
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(if (picker) stringResource(Res.string.journal_merge_confirm) else title) },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !merging) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.journal_merge_back))
                        }
                    },
                )
            },
            content = body,
        )
    }
}

@Composable
private fun ReviewContent(
    preview: JournalMergePreview,
    recovery: Boolean,
    merging: Boolean,
    error: JournalMergeError?,
    onChangeDestination: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val failureNotice = remember { BringIntoViewRequester() }
    LaunchedEffect(error) {
        if (error == JournalMergeError.Failed) failureNotice.bringIntoView()
    }
    Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(Res.string.journal_merge_from), style = MaterialTheme.typography.labelLarge)
            JournalMergeSummary(preview.source)
            Text(stringResource(Res.string.journal_merge_keep), style = MaterialTheme.typography.labelLarge)
            JournalMergeSummary(preview.destination)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                pluralStringResource(Res.plurals.journal_merge_combined, preview.combinedCount, preview.combinedCount),
                style = MaterialTheme.typography.titleMedium,
            )
            if (preview.overlapCount > 0) {
                Text(pluralStringResource(Res.plurals.journal_merge_overlap, preview.overlapCount, preview.overlapCount))
            }
        }
        Text(
            stringResource(
                if (recovery) Res.string.journal_merge_recovery_explanation else Res.string.journal_merge_explanation,
                preview.source.title,
                preview.destination.title,
            ),
        )
        error?.let {
            MergeNotice(
                stringResource(
                    if (it == JournalMergeError.Changed) Res.string.journal_merge_changed else Res.string.journal_merge_failed,
                ),
                error = true,
                modifier = Modifier.bringIntoViewRequester(failureNotice),
            )
        }
        TextButton(onClick = onChangeDestination, enabled = !merging) {
            Text(stringResource(Res.string.journal_merge_change_destination))
        }
        Button(onClick = onConfirm, enabled = !merging, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (merging) Res.string.journal_merge_merging else Res.string.journal_merge_confirm))
        }
        TextButton(onClick = onCancel, enabled = !merging, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.journal_merge_cancel))
        }
    }
}

@Composable
private fun JournalMergeSummary(
    journal: Journal,
    detail: String? = null,
    showUpdated: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = MaterialTheme.shapes.small, color = deriveCoverColor(journal.id)) {
            Box(Modifier.size(width = 48.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                if (journal.coverImageUri != null) {
                    AsyncImage(journal.coverImageUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MenuBook, null, tint = androidx.compose.ui.graphics.Color.Black)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(journal.title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (showUpdated) {
                Text(
                    stringResource(Res.string.journal_merge_updated, journal.lastUpdated.toReadableDateShort()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MergeNotice(
    text: String,
    error: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.padding(horizontal = Spacing.lg).semantics {
            if (error) liveRegion = LiveRegionMode.Polite
        },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
