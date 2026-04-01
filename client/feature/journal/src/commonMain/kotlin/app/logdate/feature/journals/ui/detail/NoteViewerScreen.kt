@file:Suppress(
    "ktlint:standard:function-naming",
)
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.feature.editor.ui.audio.expansion.ImmersiveAudioScreen
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.feature.journals.ui.AddToJournalPicker
import app.logdate.feature.journals.ui.deriveCoverColor
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys.EDITOR_TRANSITION
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.add_to_journal
import logdate.client.feature.journal.generated.resources.back
import logdate.client.feature.journal.generated.resources.cd_next_entry
import logdate.client.feature.journal.generated.resources.cd_previous_entry
import logdate.client.feature.journal.generated.resources.error
import logdate.client.feature.journal.generated.resources.image_note
import logdate.client.feature.journal.generated.resources.location
import logdate.client.feature.journal.generated.resources.open_in_locations
import logdate.client.feature.journal.generated.resources.pinned_location
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

/**
 * Viewer screen that renders notes with type-specific presentation.
 *
 * When [journalId] is provided, the viewer connects to the journal with
 * an accent color spine, the journal title in the toolbar, and prev/next navigation.
 */
@Composable
fun NoteViewerScreen(
    noteId: Uuid,
    onGoBack: () -> Unit,
    journalId: Uuid? = null,
    onOpenLocationTimeline: () -> Unit = {},
    onNavigateToNote: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NoteViewerViewModel =
        koinViewModel(
            parameters = { parametersOf(noteId, journalId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val allJournals by viewModel.allJournals.collectAsState()
    val memberJournalIds by viewModel.memberJournalIds.collectAsState()
    var showAddToJournal by remember { mutableStateOf(false) }

    when (val state = uiState) {
        NoteViewerUiState.Loading -> {
            NoteViewerLoadingContent(modifier = modifier)
        }
        is NoteViewerUiState.Error -> {
            NoteViewerErrorContent(
                message = state.message,
                modifier = modifier,
            )
        }
        is NoteViewerUiState.AudioContent -> {
            AudioNoteViewerEntry(
                noteId = state.shared.noteId,
                onGoBack = onGoBack,
                modifier = modifier,
            )
        }
        is NoteViewerUiState.TextContent -> {
            NoteViewerScaffoldContent(
                shared = state.shared,
                onGoBack = onGoBack,
                onOpenLocationTimeline = onOpenLocationTimeline,
                onNavigateToNote = onNavigateToNote,
                onShowAddToJournal = { showAddToJournal = true },
                modifier = modifier,
            ) {
                TextNoteViewer(
                    text = state.text,
                    shared = state.shared,
                )
            }
        }
        is NoteViewerUiState.ImageContent -> {
            NoteViewerScaffoldContent(
                shared = state.shared,
                onGoBack = onGoBack,
                onOpenLocationTimeline = onOpenLocationTimeline,
                onNavigateToNote = onNavigateToNote,
                onShowAddToJournal = { showAddToJournal = true },
                modifier = modifier,
            ) {
                ImageNoteViewer(
                    mediaRef = state.mediaRef,
                    shared = state.shared,
                )
            }
        }
        is NoteViewerUiState.VideoContent -> {
            NoteViewerScaffoldContent(
                shared = state.shared,
                onGoBack = onGoBack,
                onOpenLocationTimeline = onOpenLocationTimeline,
                onNavigateToNote = onNavigateToNote,
                onShowAddToJournal = { showAddToJournal = true },
                modifier = modifier,
            ) {
                VideoNoteViewer(mediaRef = state.mediaRef)
            }
        }
    }

    if (showAddToJournal) {
        AddToJournalPicker(
            noteId = noteId,
            currentJournalId = journalId,
            journals = allJournals,
            memberJournalIds = memberJournalIds,
            onToggleMembership = viewModel::toggleJournalMembership,
            onDismiss = { showAddToJournal = false },
        )
    }
}

// region Type-specific viewers

/**
 * Text note rendered as a page with generous typography and a journal spine accent.
 */
@Composable
private fun TextNoteViewer(
    text: String,
    shared: NoteViewerShared,
    modifier: Modifier = Modifier,
) {
    val journalContext = shared.journalContext
    val accentColor =
        journalContext?.let {
            remember(it.journalId) { deriveCoverColor(it.journalId) }
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row {
            if (accentColor != null) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(accentColor),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = text,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 28.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                Text(
                    text = shared.createdAt.toReadableDateTimeShort(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * Image note rendered edge-to-edge with shadow and a journal accent bar.
 */
@Composable
private fun ImageNoteViewer(
    mediaRef: String,
    shared: NoteViewerShared,
    modifier: Modifier = Modifier,
) {
    val journalContext = shared.journalContext
    val accentColor =
        journalContext?.let {
            remember(it.journalId) { deriveCoverColor(it.journalId) }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = mediaRef,
            contentDescription = stringResource(Res.string.image_note),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Fit,
        )

        if (accentColor != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(accentColor),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(
            text = shared.createdAt.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/**
 * Video note rendered as a player filling the width.
 */
@Composable
private fun VideoNoteViewer(
    mediaRef: String,
    modifier: Modifier = Modifier,
) {
    VideoPlayerContent(
        uri = mediaRef,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
    )
}

// endregion

// region Audio viewer

/**
 * Audio note presentation delegated to the immersive audio screen.
 */
@Composable
private fun AudioNoteViewerEntry(
    noteId: Uuid,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioNoteViewerViewModel =
        koinViewModel(
            parameters = { parametersOf(noteId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsState()
    AudioNoteViewerContent(
        uiState = uiState,
        onGoBack = onGoBack,
        onPlayPause = viewModel::togglePlayback,
        onSeek = viewModel::seekTo,
        onSkipBack = { viewModel.skipByMillis(-10_000L) },
        onSkipForward = { viewModel.skipByMillis(10_000L) },
        modifier = modifier,
    )
}

// endregion

// region Scaffold and toolbar

/**
 * Shared immersive layout for non-audio note presentations.
 * Provides the toolbar with journal context and wraps content in [ImmersiveEditorLayout].
 */
@Composable
fun NoteViewerScaffoldContent(
    shared: NoteViewerShared,
    onGoBack: () -> Unit,
    onOpenLocationTimeline: () -> Unit = {},
    onNavigateToNote: (Uuid) -> Unit = {},
    onShowAddToJournal: () -> Unit = {},
    modifier: Modifier = Modifier,
    noteContent: @Composable () -> Unit,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val layoutModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedTransitionScope.rememberSharedContentState(EDITOR_TRANSITION),
                    animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }

    val journalContext = shared.journalContext
    val accentColor =
        journalContext?.let {
            remember(it.journalId) { deriveCoverColor(it.journalId) }
        }

    ImmersiveEditorLayout(
        topBarContent = {
            NoteViewerToolbar(
                onGoBack = onGoBack,
                journalContext = journalContext,
                accentColor = accentColor,
                onNavigateToNote = onNavigateToNote,
                onShowAddToJournal = onShowAddToJournal,
            )
        },
        editorContent = {
            NoteViewerContent(
                shared = shared,
                onOpenLocationTimeline = onOpenLocationTimeline,
                noteContent = noteContent,
            )
        },
        bottomContent = {
            Spacer(modifier = Modifier.fillMaxWidth())
        },
        modifier = modifier.then(layoutModifier),
    )
}

/**
 * Navigation toolbar with journal context and prev/next navigation.
 */
@Composable
private fun NoteViewerToolbar(
    onGoBack: () -> Unit,
    journalContext: JournalContext? = null,
    accentColor: Color? = null,
    onNavigateToNote: (Uuid) -> Unit = {},
    onShowAddToJournal: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onGoBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
            )
        }

        if (journalContext != null) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = journalContext.journalTitle,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onShowAddToJournal) {
            Icon(
                Icons.Default.LibraryAdd,
                contentDescription = stringResource(Res.string.add_to_journal),
            )
        }

        if (journalContext != null) {
            IconButton(
                onClick = { journalContext.previousNoteId?.let(onNavigateToNote) },
                enabled = journalContext.hasPrevious,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.cd_previous_entry),
                )
            }
            IconButton(
                onClick = { journalContext.nextNoteId?.let(onNavigateToNote) },
                enabled = journalContext.hasNext,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.cd_next_entry),
                )
            }
        }
    }
}

/**
 * Note body wrapper — handles location card and content placement.
 * No Card/Surface wrapper; each type-specific viewer handles its own surface treatment.
 */
@Composable
fun NoteViewerContent(
    shared: NoteViewerShared,
    onOpenLocationTimeline: () -> Unit = {},
    noteContent: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        shared.location?.let { location ->
            NoteLocationCard(
                location = location,
                onOpenLocationTimeline = onOpenLocationTimeline,
            )
        }

        noteContent()
    }
}

// endregion

// region Supporting components

@Composable
private fun NoteLocationCard(
    location: NoteLocation,
    onOpenLocationTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coordinates = location.coordinates
    val place = location.place
    val title = location.displayName ?: stringResource(Res.string.pinned_location)
    val subtitle =
        when {
            coordinates != null ->
                "${coordinates.latitude.formatCoordinate()}, ${coordinates.longitude.formatCoordinate()}"
            place != null ->
                "${place.latitude.formatCoordinate()}, ${place.longitude.formatCoordinate()}"
            else -> ""
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onOpenLocationTimeline,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.location),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FilledTonalButton(onClick = onOpenLocationTimeline) {
                Text(text = stringResource(Res.string.open_in_locations))
            }
        }
    }
}

@Composable
fun NoteViewerLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun NoteViewerErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.error),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Double.formatCoordinate(): String {
    val roundedScaled = (this * 10_000).roundToLong()
    val absoluteScaled = roundedScaled.absoluteValue
    val wholePart = absoluteScaled / 10_000
    val fractionPart = (absoluteScaled % 10_000).toString().padStart(4, '0')
    val sign = if (roundedScaled < 0) "-" else ""

    return "$sign$wholePart.$fractionPart"
}

// endregion

// region Audio viewer content

@Composable
fun AudioNoteViewerContent(
    uiState: AudioNoteViewerUiState,
    onGoBack: () -> Unit,
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        AudioNoteViewerUiState.Loading -> {
            NoteViewerLoadingContent(modifier = modifier)
        }

        is AudioNoteViewerUiState.Error -> {
            NoteViewerErrorContent(
                message = uiState.message,
                modifier = modifier,
            )
        }

        is AudioNoteViewerUiState.Ready -> {
            ImmersiveAudioScreen(
                amplitudes = uiState.context.amplitudes,
                progress = uiState.playbackState.progress,
                isPlaying = uiState.playbackState.isPlaying,
                palette = uiState.context.palette,
                daylightPeriod = uiState.context.daylightPeriod,
                durationMs = uiState.durationMs,
                createdAt = uiState.createdAt,
                segments = uiState.context.segments,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onClose = onGoBack,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

// endregion
