@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.audio.AudioLabelResolver
import app.logdate.feature.editor.audio.color.PaletteGenerator
import app.logdate.feature.editor.audio.formatAudioLabel
import app.logdate.feature.editor.ui.audio.AnimatedPlayPauseButton
import app.logdate.feature.journals.ui.deriveCoverColor
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.applyStandardContentWidth
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.uuid.Uuid
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * The main screen to view a journal's contents.
 */
@Composable
fun JournalDetailScreen(
    journalId: Uuid,
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit = { _ -> },
    onOpenEditor: (Uuid) -> Unit = {},
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(journalId) {
        viewModel.setSelectedJournalId(journalId)
    }

    val state by viewModel.uiState.collectAsState()
    var openDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var noteToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    JournalDetailScreenContent(
        uiState = state,
        onGoBack = onGoBack,
        onNavigateToNoteDetail = onNavigateToNoteDetail,
        onOpenEditor = { onOpenEditor(journalId) },
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToShare = onNavigateToShare,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onRequestDelete = { openDeleteConfirmation = true },
        showDeleteConfirmation = openDeleteConfirmation,
        onDismissDeleteConfirmation = { openDeleteConfirmation = false },
        onConfirmDelete = {
            viewModel.deleteJournal(onJournalDeleted)
            openDeleteConfirmation = false
        },
        onRemoveNoteFromJournal = { noteId -> noteToRemove = noteId.toString() },
        showRemoveNoteConfirmation = noteToRemove != null,
        onDismissRemoveNoteConfirmation = { noteToRemove = null },
        onConfirmRemoveNote = {
            noteToRemove?.let { viewModel.removeNoteFromJournal(Uuid.parse(it)) }
            noteToRemove = null
        },
        modifier = modifier,
    )
}

@Composable
fun JournalDetailScreenContent(
    uiState: JournalDetailUiState,
    onGoBack: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit = { _ -> },
    onOpenEditor: () -> Unit = {},
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    showDeleteConfirmation: Boolean = false,
    onDismissDeleteConfirmation: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onRemoveNoteFromJournal: (noteId: Uuid) -> Unit = {},
    showRemoveNoteConfirmation: Boolean = false,
    onDismissRemoveNoteConfirmation: () -> Unit = {},
    onConfirmRemoveNote: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    when (uiState) {
        is JournalDetailUiState.Loading -> {
            JournalDetailPlaceholder()
            return
        }

        is JournalDetailUiState.Error -> {
            // Preserve the current empty error branch so previews match the route's real behavior.
            return
        }

        is JournalDetailUiState.Success -> {
            var showOverflowMenu by remember { mutableStateOf(false) }
            var selectedTab by remember { mutableStateOf(0) }
            val mediaEntries =
                remember(uiState.entries) {
                    uiState.entries.filter { it is EntryDisplayData.ImageEntry || it is EntryDisplayData.VideoEntry }
                }
            val hasMedia = mediaEntries.isNotEmpty()

            Scaffold(
                modifier =
                    modifier
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .let { baseModifier ->
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    baseModifier.sharedElement(
                                        rememberSharedContentState(
                                            TransitionKeys.journalContainerTransition(uiState.journalId),
                                        ),
                                        animatedVisibilityScope,
                                    )
                                }
                            } else {
                                baseModifier
                            }
                        },
                contentWindowInsets = WindowInsets.navigationBars,
                floatingActionButton = {
                    FloatingActionButton(onClick = onOpenEditor) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(Res.string.create_new_entry))
                    }
                },
                topBar = {
                    LargeTopAppBar(
                        title = { Text(uiState.title) },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(UiRes.string.common_back),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = onToggleSortOrder) {
                                val sortIcon =
                                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                        Icons.Rounded.ArrowDownward
                                    } else {
                                        Icons.Rounded.ArrowUpward
                                    }

                                val description =
                                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                        "Sorted: Newest first (click to show oldest first)"
                                    } else {
                                        "Sorted: Oldest first (click to show newest first)"
                                    }

                                Icon(
                                    sortIcon,
                                    contentDescription = description,
                                )
                            }

                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(Res.string.journal_settings_2),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.share_journal_2)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToShare(uiState.journalId)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Share, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.journal_settings_2)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToSettings(uiState.journalId)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Settings, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.delete_journal_2)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onRequestDelete()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
            ) { paddingValues ->
                val listState = rememberLazyListState()

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                ) {
                    if (hasMedia && uiState.entries.isNotEmpty()) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(Res.string.tab_timeline)) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(Res.string.tab_gallery)) },
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (selectedTab == 1 && hasMedia) {
                            JournalGalleryGrid(
                                mediaEntries = mediaEntries,
                                onOpenEntry = onNavigateToNoteDetail,
                                modifier =
                                    Modifier
                                        .padding(vertical = Spacing.sm)
                                        .applyStandardContentWidth(),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(vertical = Spacing.sm)
                                        .applyStandardContentWidth(),
                            ) {
                                if (uiState.entries.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            stringResource(Res.string.no_entries_in_this_journal_yet),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(Modifier.height(Spacing.sm))
                                        Text(
                                            stringResource(Res.string.journal_empty_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    val groupedEntries =
                                        remember(uiState.entries) {
                                            groupEntriesByDay(uiState.entries)
                                        }

                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = Spacing.sm),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text =
                                                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                                        stringResource(Res.string.sort_newest_first)
                                                    } else {
                                                        stringResource(Res.string.sort_oldest_first)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding =
                                                PaddingValues(
                                                    bottom = Spacing.xl,
                                                ),
                                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        ) {
                                            groupedEntries.forEach { (dateLabel, entries) ->
                                                item(key = "header-$dateLabel") {
                                                    DaySectionHeader(dateLabel)
                                                }
                                                entries.forEach { entry ->
                                                    item(key = "entry-${entry.id}") {
                                                        JournalEntryItem(
                                                            entry = entry,
                                                            onClick = { onNavigateToNoteDetail(entry.id) },
                                                            onRemoveFromJournal = { onRemoveNoteFromJournal(entry.id) },
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
            }

            if (showDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onDismissRequest = onDismissDeleteConfirmation,
                    onConfirmation = onConfirmDelete,
                )
            }

            if (showRemoveNoteConfirmation) {
                RemoveNoteFromJournalDialog(
                    onDismissRequest = onDismissRemoveNoteConfirmation,
                    onConfirmation = onConfirmRemoveNote,
                )
            }
        }
    }
}

/**
 * Groups a sorted list of entries by their calendar day, preserving order.
 */
private fun groupEntriesByDay(entries: List<EntryDisplayData>): List<Pair<String, List<EntryDisplayData>>> {
    val tz = TimeZone.currentSystemDefault()
    val grouped = linkedMapOf<String, MutableList<EntryDisplayData>>()
    for (entry in entries) {
        val date = entry.timestamp.toLocalDateTime(tz).date
        val label = date.toReadableDateShort()
        grouped.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return grouped.map { (label, items) -> label to items.toList() }
}

@Composable
private fun DaySectionHeader(
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = dateLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

// region Entry type composables

@Composable
private fun JournalEntryItem(
    entry: EntryDisplayData,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val noteViewerSharedBoundsModifier =
        rememberNoteViewerSharedBoundsModifier(entry.id)

    Column(modifier = modifier) {
        when (entry) {
            is EntryDisplayData.TextEntry ->
                TextEntryCard(
                    entry = entry,
                    onClick = onClick,
                    onRemoveFromJournal = onRemoveFromJournal,
                    cardModifier = noteViewerSharedBoundsModifier,
                )
            is EntryDisplayData.ImageEntry ->
                ImageEntryCard(
                    entry = entry,
                    onClick = onClick,
                    onRemoveFromJournal = onRemoveFromJournal,
                    cardModifier = noteViewerSharedBoundsModifier,
                )
            is EntryDisplayData.VideoEntry ->
                VideoEntryCard(
                    entry = entry,
                    onClick = onClick,
                    onRemoveFromJournal = onRemoveFromJournal,
                    cardModifier = noteViewerSharedBoundsModifier,
                )
            is EntryDisplayData.AudioEntry ->
                AudioEntryCard(
                    entry = entry,
                    onClick = onClick,
                    onRemoveFromJournal = onRemoveFromJournal,
                    cardModifier = noteViewerSharedBoundsModifier,
                )
        }

        if (entry.otherJournals.isNotEmpty()) {
            JournalMembershipBadges(
                journals = entry.otherJournals,
                onNavigateToJournal = onNavigateToJournal,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun rememberNoteViewerSharedBoundsModifier(noteId: Uuid): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    return if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(TransitionKeys.noteViewerTransition(noteId)),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else {
        Modifier
    }
}

/**
 * Row of small pills showing the other journals this entry appears in.
 * Each pill shows the journal's derived color and title, and is tappable.
 */
@Composable
private fun JournalMembershipBadges(
    journals: List<JournalReference>,
    onNavigateToJournal: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "Also in",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        journals.forEach { journal ->
            val color = remember(journal.id) { deriveCoverColor(journal.id) }
            Surface(
                onClick = { onNavigateToJournal(journal.id) },
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.2f),
                modifier = Modifier.heightIn(max = 24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(color, MaterialTheme.shapes.extraSmall),
                    )
                    Text(
                        text = journal.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextEntryCard(
    entry: EntryDisplayData.TextEntry,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    InlineEntryCardShell(
        timestamp = entry.timestamp,
        onClick = {
            if (expanded) onClick() else expanded = true
        },
        onRemoveFromJournal = onRemoveFromJournal,
        modifier = modifier,
        cardModifier = cardModifier,
    ) {
        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .animateContentSize()
                    .heightIn(min = 40.dp),
        )
    }
}

@Composable
private fun ImageEntryCard(
    entry: EntryDisplayData.ImageEntry,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    VerticalEntryCardShell(
        timestamp = entry.timestamp,
        onClick = onClick,
        onRemoveFromJournal = onRemoveFromJournal,
        modifier = modifier,
        cardModifier = cardModifier,
    ) {
        AsyncImage(
            model = entry.mediaRef,
            contentDescription = stringResource(Res.string.image_note),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(AspectRatios.RATIO_4_3)
                    .clip(RoundedCornerShape(Spacing.sm)),
            contentScale = ContentScale.Crop,
        )
        if (entry.caption.isNotBlank()) {
            Text(
                text = entry.caption,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun VideoEntryCard(
    entry: EntryDisplayData.VideoEntry,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    VerticalEntryCardShell(
        timestamp = entry.timestamp,
        onClick = onClick,
        onRemoveFromJournal = onRemoveFromJournal,
        modifier = modifier,
        cardModifier = cardModifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(AspectRatios.RATIO_16_9)
                    .clip(RoundedCornerShape(Spacing.sm)),
        ) {
            AsyncImage(
                model = entry.mediaRef,
                contentDescription = stringResource(Res.string.video_note),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Play indicator overlay
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            Icon(
                Icons.Rounded.Videocam,
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm)
                        .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        if (entry.caption.isNotBlank()) {
            Text(
                text = entry.caption,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun AudioEntryCard(
    entry: EntryDisplayData.AudioEntry,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    val audioPlaybackState = LocalAudioPlaybackState.current
    val isCurrentEntry = audioPlaybackState.currentlyPlayingId == entry.id
    val isEntryPlaying = isCurrentEntry && audioPlaybackState.isPlaying

    val labelResolver = remember { AudioLabelResolver() }
    val labelResult =
        remember(entry.timestamp, entry.locationName) {
            labelResolver.resolve(
                createdAt = entry.timestamp,
                locationName = entry.locationName,
            )
        }
    val resolvedTitle = formatAudioLabel(labelResult)

    // Derive palette from daylight period for the progress indicator and mini-player
    val paletteGenerator = remember { PaletteGenerator() }
    val palette =
        remember(labelResult) {
            when (labelResult) {
                is app.logdate.feature.editor.audio.AudioLabelResult.Contextual ->
                    paletteGenerator.generate(labelResult.period)
                else -> null
            }
        }
    val accentColor = palette?.let { Color(it.accentColor) }

    // Smooth progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = if (isCurrentEntry) audioPlaybackState.progress else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "AudioCardProgress",
    )

    InlineEntryCardShell(
        timestamp = entry.timestamp,
        onClick = onClick,
        onRemoveFromJournal = onRemoveFromJournal,
        modifier = modifier,
        cardModifier = cardModifier,
    ) {
        AnimatedPlayPauseButton(
            isPlaying = isEntryPlaying,
            onClick = {
                if (isEntryPlaying) {
                    audioPlaybackState.pause()
                } else {
                    audioPlaybackState.play(
                        entry.id,
                        entry.mediaRef,
                        AudioPlaybackDisplayInfo(
                            title = resolvedTitle,
                            subtitle = if (entry.durationMs > 0) formatAudioDuration(entry.durationMs) else null,
                            accentColor = palette?.accentColor,
                        ),
                    )
                }
            },
            modifier =
                Modifier
                    .size(40.dp)
                    .testTag("journal-audio-playback-button"),
            iconSize = 20.dp,
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                color = accentColor ?: MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(2.dp))
            if (entry.durationMs > 0) {
                Text(
                    text = formatAudioDuration(entry.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Formats milliseconds into a human-readable duration string (e.g. "1:23" or "0:05").
 */
private fun formatAudioDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// endregion

// region Entry card shell

/**
 * Card wrapper for entry types that display media content filling the width.
 * Renders a timestamp label above the card, media content, and an overflow menu.
 */
@Composable
private fun VerticalEntryCardShell(
    timestamp: Instant,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = timestamp.localTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    EntryOverflowMenu(
                        showMenu = showMenu,
                        onShowMenu = { showMenu = true },
                        onDismiss = { showMenu = false },
                        onRemoveFromJournal = {
                            showMenu = false
                            onRemoveFromJournal()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Card wrapper for inline entry types (text, audio) displayed in a row
 * alongside the overflow menu.
 */
@Composable
private fun InlineEntryCardShell(
    timestamp: Instant,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = timestamp.localTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                content()
                EntryOverflowMenu(
                    showMenu = showMenu,
                    onShowMenu = { showMenu = true },
                    onDismiss = { showMenu = false },
                    onRemoveFromJournal = {
                        showMenu = false
                        onRemoveFromJournal()
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryOverflowMenu(
    showMenu: Boolean,
    onShowMenu: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveFromJournal: () -> Unit,
) {
    Box {
        IconButton(onClick = onShowMenu) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(Res.string.journal_settings_2),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remove_from_journal)) },
                onClick = onRemoveFromJournal,
                leadingIcon = {
                    Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null)
                },
            )
        }
    }
}

// endregion

// region Dialogs

@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    icon: ImageVector = Icons.Rounded.Warning,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.delete_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirmation,
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
    )
}

@Composable
internal fun JournalDetailPlaceholder() {
    Row(
        modifier =
            Modifier
                .padding(Spacing.lg)
                .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading...",
        )
    }
}

@Composable
private fun RemoveNoteFromJournalDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(Icons.Rounded.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.remove_from_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.remove_from_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirmation) {
                Text(stringResource(Res.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
    )
}

// endregion
