package app.logdate.feature.timeline.ui.details

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
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.NoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage

@Composable
internal fun NotesListSection(
    notes: List<NoteUiState>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Notes", style = MaterialTheme.typography.titleSmall)
        notes.forEach { note ->
            when (note) {
                is TextNoteUiState -> TextNoteSnippet(note)
                is ImageNoteUiState -> ImageNoteSnippet(note)
                is AudioNoteUiState -> {
                    // Just use the AudioNoteSnippet without any DI or service injection
                    // The AudioNoteSnippet already uses LocalAudioPlaybackState internally
                    AudioNoteSnippet(
                        uiState = note,
                    )
                }
                is VideoNoteUiState -> VideoNoteSnippet(note)
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
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(Spacing.md),
            ) {
                Text(
                    text = uiState.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.padding(Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uiState.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
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
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.padding(Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                // Use the thumbnail if available, otherwise use the video URI directly
                // (which may display a thumbnail or first frame depending on the platform)
                AsyncImage(
                    model = uiState.thumbnailUri ?: uiState.uri,
                    contentDescription = "Video recording",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // In the future, we can add a play button overlay and video player here
            }
        }
    }
}