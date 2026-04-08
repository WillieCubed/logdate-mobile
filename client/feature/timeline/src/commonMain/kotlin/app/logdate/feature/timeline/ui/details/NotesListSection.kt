@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.noteDragSource
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.JournalBadgeRow
import app.logdate.ui.timeline.NoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage
import logdate.client.feature.timeline.generated.resources.*
import logdate.client.feature.timeline.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun NotesListSection(
    notes: List<NoteUiState>,
    onJournalClick: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(Res.string.notes), style = MaterialTheme.typography.titleSmall)
        notes.forEach { note ->
            Column {
                when (note) {
                    is TextNoteUiState -> TextNoteSnippet(note)
                    is ImageNoteUiState -> ImageNoteSnippet(note)
                    is AudioNoteUiState -> {
                        AudioNoteSnippet(
                            uiState = note,
                        )
                    }
                    is VideoNoteUiState -> VideoNoteSnippet(note)
                }
                JournalBadgeRow(journals = note.journals, onJournalClick = onJournalClick)
            }
        }
    }
}

@Composable
private fun TextNoteSnippet(uiState: TextNoteUiState) {
    Column(
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        // Date/time displayed above the card
        Text(
            text = uiState.timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().noteDragSource(uiState.noteId.toString()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(Spacing.md),
            ) {
                Text(
                    text = uiState.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ImageNoteSnippet(uiState: ImageNoteUiState) {
    Column(
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        // Date/time displayed above the card
        Text(
            text = uiState.timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().noteDragSource(uiState.noteId.toString()),
        ) {
            Box {
                AsyncImage(
                    model = uiState.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.caption.isNotBlank()) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                    ),
                                ).padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = uiState.caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoNoteSnippet(uiState: VideoNoteUiState) {
    Column(
        modifier = Modifier.padding(vertical = Spacing.xs),
    ) {
        // Date/time displayed above the card
        Text(
            text = uiState.timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().noteDragSource(uiState.noteId.toString()),
        ) {
            Box {
                AsyncImage(
                    model = uiState.thumbnailUri ?: uiState.uri,
                    contentDescription = stringResource(Res.string.video_recording),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.caption.isNotBlank()) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                    ),
                                ).padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = uiState.caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
