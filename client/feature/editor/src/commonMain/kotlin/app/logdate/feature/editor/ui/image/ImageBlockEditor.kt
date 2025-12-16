package app.logdate.feature.editor.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * A component that handles image display and editing within the editor.
 * 
 * This component renders an image with optional caption and provides controls
 * for editing or deleting the image.
 * 
 * @param block The image block state
 * @param onBlockUpdated Callback when the block is updated
 * @param onDeleteRequested Callback when the block should be deleted
 * @param modifier Modifier for layout customization
 * @param viewModel The view model for handling image picking logic
 */
@Composable
fun ImageBlockEditor(
    block: ImageBlockUiState,
    onBlockUpdated: (ImageBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageBlockViewModel = koinViewModel()
) {
    // Determine if we have an existing image
    val hasExistingImage = block.uri != null
    
    // Get the state from the view model
    val uiState by viewModel.uiState.collectAsState()
    
    // When the view model provides a selected image URI, update the block
    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { uri ->
            if (block.uri == null) {
                Napier.d("ImageBlockEditor - Setting image URI from view model: $uri")
                onBlockUpdated(block.copy(uri = uri))
                viewModel.clearSelectedImage() // Clear to avoid duplicate updates
            }
        }
    }
    
    // Debug logging
    Napier.d("ImageBlockEditor - hasExistingImage: $hasExistingImage, URI: ${block.uri}")
    
    // Show either the existing image with caption or the image picker UI
    if (hasExistingImage) {
        // Show the image with caption field
        Column(
            modifier = modifier.fillMaxWidth().padding(8.dp)
        ) {
            // Image display
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(block.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = block.caption.ifBlank { "Image" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp) // Fixed height for consistent UI
                        .clip(RoundedCornerShape(8.dp))
                )
                
                // Delete button overlay
                IconButton(
                    onClick = onDeleteRequested,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete image",
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
            
            // Caption field
            OutlinedTextField(
                value = block.caption,
                onValueChange = { newCaption ->
                    onBlockUpdated(block.copy(caption = newCaption))
                },
                placeholder = { Text("Add a caption...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
        }
    } else {
        // Show image picker UI when no image exists yet
        ImagePickerContent(
            onImageSelected = { uri ->
                onBlockUpdated(block.copy(uri = uri))
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )
    }
}

/**
 * The content shown when no image has been selected yet.
 * This is a platform-specific implementation that should be defined in each platform.
 */
@Composable
expect fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
)