@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.logdate.feature.editor.ui.audio.expansion.ImmersiveAudioScreen
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys.EDITOR_TRANSITION
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/**
 * Viewer screen that renders notes with type-specific presentation.
 */
@Composable
fun NoteViewerScreen(
    noteId: Uuid,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewerViewModel =
        koinViewModel(
            parameters = { parametersOf(noteId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                modifier = modifier,
            ) {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        is NoteViewerUiState.ImageContent -> {
            NoteViewerScaffoldContent(
                shared = state.shared,
                onGoBack = onGoBack,
                modifier = modifier,
            ) {
                NoteViewerImageContent(mediaRef = state.mediaRef)
            }
        }
        is NoteViewerUiState.VideoContent -> {
            NoteViewerScaffoldContent(
                shared = state.shared,
                onGoBack = onGoBack,
                modifier = modifier,
            ) {
                NoteViewerVideoContent(mediaRef = state.mediaRef)
            }
        }
    }
}

@Composable
fun NoteViewerImageContent(
    mediaRef: String,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.image),
            style = MaterialTheme.typography.titleMedium,
        )
        AsyncImage(
            model = mediaRef,
            contentDescription = stringResource(Res.string.image_note),
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.xxl * 4),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun NoteViewerVideoContent(
    mediaRef: String,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.video),
            style = MaterialTheme.typography.titleMedium,
        )
        VideoPlayerContent(
            uri = mediaRef,
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.xxl * 4),
        )
    }
}

/**
 * Audio note presentation for the viewer.
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

/**
 * Shared layout for non-audio note presentations.
 */
@Composable
fun NoteViewerScaffoldContent(
    shared: NoteViewerShared,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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

    ImmersiveEditorLayout(
        topBarContent = {
            NoteViewerToolbar(onGoBack = onGoBack)
        },
        editorContent = {
            NoteViewerContent(shared = shared, content = content)
        },
        bottomContent = {
            Spacer(modifier = Modifier.fillMaxWidth())
        },
        modifier = modifier.then(layoutModifier),
    )
}

/**
 * Navigation toolbar for the note viewer.
 */
@Composable
private fun NoteViewerToolbar(onGoBack: () -> Unit) {
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
    }
}

/**
 * Note body content for non-audio presentations.
 */
@Composable
fun NoteViewerContent(
    shared: NoteViewerShared,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = shared.createdAt.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(Spacing.lg),
                color = MaterialTheme.colorScheme.surface,
            ) {
                content()
            }
        }
    }
}

/**
 * Loading state for note viewing.
 */
@Composable
fun NoteViewerLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Error state for note viewing.
 */
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

/**
 * Audio note presentation for note viewer previews and route rendering.
 */
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
