package app.logdate.feature.editor.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.DeleteMediaButton
import app.logdate.feature.editor.ui.common.MediaCaptionField
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import logdate.client.feature.editor.generated.resources.Res
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
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(block.uri)
                            .build(),
                    contentDescription = block.caption.ifBlank { "Image" },
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                )

                DeleteMediaButton(
                    onClick = onDeleteRequested,
                    contentDescription = stringResource(Res.string.delete_image),
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            MediaCaptionField(
                caption = block.caption,
                onCaptionChanged = { onBlockUpdated(block.copy(caption = it)) },
            )
        }
    } else {
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
expect fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
)
