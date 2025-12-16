package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.aakira.napier.Napier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Constants for spacing and sizing
private val spacing = 16.dp
private val cornerRadius = 16.dp
private val minHeight = 140.dp
private val photoMinHeight = 200.dp

/**
 * A component for adding new blocks when the editor is empty.
 *
 * This provides interactive buttons to create a new content block, rendering
 * them in a responsive layout that adapts to the parent component.
 */
@Composable
fun EmptyEditorStateContent(
    onStartTextBlock: () -> Unit,
    onStartPhotoBlock: () -> Unit,
    onStartAudioBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use the entire size of the parent with some padding
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        // Top row with text and audio buttons - each takes equal height in the top half
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Take proportional space in the top half
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            // Text surface - fills half the width and full available height in this row
            TextEntrySurface(
                onClick = onStartTextBlock,
                modifier = Modifier
                    .weight(1f) // Take equal space with audio button
                    .fillMaxSize() // Fill all available height in this row
            )
            
            // Audio surface - fills half the width and full available height in this row
            AudioRecordingSurface(
                onClick = onStartAudioBlock,
                modifier = Modifier
                    .weight(1f) // Take equal space with text button
                    .fillMaxSize() // Fill all available height in this row
            )
        }
        
        // Photo surface - takes full width and proportional height in the bottom half
        PhotoSurface(
            onClick = onStartPhotoBlock,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Take proportional space in the bottom half
        )
    }
}

/**
 * Surface for creating a new text entry
 */
@Composable
private fun TextEntrySurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Napier.i("TextEntrySurface: Initializing surface with onClick handler")
    Surface(
        onClick = {
            Napier.i("TextEntrySurface: onClick triggered")
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.TextFields,
                    contentDescription = "Start text entry",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Write something",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Surface for creating a new audio recording
 */
@Composable
private fun AudioRecordingSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = {
            Napier.i("AudioRecordingSurface: onClick triggered")
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Record audio",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Record audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Surface for creating a new photo entry
 */
@Composable
private fun PhotoSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = {
            Napier.i("PhotoSurface: onClick triggered")
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "Add photo",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add a photo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capture a moment or upload from your gallery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}