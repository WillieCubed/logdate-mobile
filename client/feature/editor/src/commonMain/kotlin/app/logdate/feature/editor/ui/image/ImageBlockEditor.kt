package app.logdate.feature.editor.ui.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.DeleteMediaButton
import app.logdate.feature.editor.ui.common.OverlayCaptionField
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.ui.content.ImageScrimOverlay
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_an_image_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_a_photo
import logdate.client.feature.editor.generated.resources.delete_image
import org.jetbrains.compose.resources.stringResource

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
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ImageBlockEditor(
    block: ImageBlockUiState,
    onBlockUpdated: (ImageBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasExistingImage = block.uri != null

    if (hasExistingImage) {
        val hasCaption = block.caption.isNotBlank()
        var showCaption by remember { mutableStateOf(hasCaption) }
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(block.uri)
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

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ImmersiveImagePickerEmptyState(
    onSelectImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(20.dp).size(36.dp),
                )
            }

            Spacer(modifier = Modifier.size(20.dp))

            Text(
                text = stringResource(Res.string.add_an_image_to_your_entry),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.size(12.dp))

            Text(
                text = stringResource(Res.string.choose_a_photo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.size(24.dp))

            Button(
                onClick = onSelectImage,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = stringResource(Res.string.choose_a_photo))
            }
        }
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
