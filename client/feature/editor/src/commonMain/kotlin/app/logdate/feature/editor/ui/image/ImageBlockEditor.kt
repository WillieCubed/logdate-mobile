package app.logdate.feature.editor.ui.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.DeleteMediaButton
import app.logdate.feature.editor.ui.common.OverlayCaptionField
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.ui.content.ImageScrimOverlay
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.delete_image
import org.jetbrains.compose.resources.stringResource
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
@Suppress("ktlint:standard:function-naming")
@Composable
fun ImageBlockEditor(
    block: ImageBlockUiState,
    onBlockUpdated: (ImageBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageBlockViewModel = koinViewModel(),
) {
    // Determine if we have an existing image
    val hasExistingImage = block.uri != null

    // Get the state from the view model
    val uiState by viewModel.uiState.collectAsState()

    // When the view model provides a selected image URI, update the block
    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { uri ->
            if (block.uri == null) {
                onBlockUpdated(block.copy(uri = uri))
                viewModel.clearSelectedImage() // Clear to avoid duplicate updates
            }
        }
    }

    // Show either the existing image with caption or the image picker UI
    if (hasExistingImage) {
        val hasCaption = block.caption.isNotBlank()
        var showCaption by remember { mutableStateOf(hasCaption) }
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(block.uri)
                        .crossfade(true)
                        .build(),
                contentDescription = block.caption.ifBlank { "Image" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { showCaption = !showCaption },
            )

            AnimatedVisibility(
                visible = showCaption || hasCaption,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    ImageScrimOverlay(bottomAlpha = 0.6f)
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        OverlayCaptionField(
                            caption = block.caption,
                            onCaptionChanged = { onBlockUpdated(block.copy(caption = it)) },
                        )
                    }
                }
            }

            DeleteMediaButton(
                onClick = onDeleteRequested,
                contentDescription = stringResource(Res.string.delete_image),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    } else {
        // Show image picker UI when no image exists yet
        ImagePickerContent(
            onImageSelected = { uri ->
                onBlockUpdated(block.copy(uri = uri))
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

/**
 * The content shown when no image has been selected yet.
 * This is a platform-specific implementation that should be defined in each platform.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
)
