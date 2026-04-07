@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.ui.content

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.LocalEditorIsCompact
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.capture
import logdate.client.feature.editor.generated.resources.capture_photo_or_video
import logdate.client.feature.editor.generated.resources.choose_a_photo
import logdate.client.feature.editor.generated.resources.editor_action_add_photo_gallery
import logdate.client.feature.editor.generated.resources.gallery
import logdate.client.feature.editor.generated.resources.photo_or_video
import logdate.client.feature.editor.generated.resources.record_audio
import logdate.client.feature.editor.generated.resources.start_text_entry
import logdate.client.feature.editor.generated.resources.write_something
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

// Constants for spacing and sizing
private val spacing = 16.dp
private val cornerRadius = 16.dp

internal data class EmptyEditorPickerTileIds(
    val textId: Uuid? = null,
    val audioId: Uuid? = null,
    val cameraId: Uuid? = null,
    val photoId: Uuid? = null,
)

internal fun matchingPickerTileIdsFor(block: EntryBlockUiState?): EmptyEditorPickerTileIds =
    when (block) {
        is TextBlockUiState -> EmptyEditorPickerTileIds(textId = block.id)
        is AudioBlockUiState -> EmptyEditorPickerTileIds(audioId = block.id)
        is CameraBlockUiState -> EmptyEditorPickerTileIds(cameraId = block.id)
        is ImageBlockUiState -> EmptyEditorPickerTileIds(photoId = block.id)
        else -> EmptyEditorPickerTileIds()
    }

/**
 * A component for adding new blocks when the editor is empty.
 *
 * Each tile pre-generates a block ID so that [sharedBounds] connects the tile's
 * bounds to the expanded block surface, producing a container-morph transition.
 *
 * On height-constrained screens (e.g. landscape phones) where [LocalEditorIsCompact]
 * is true, the four tiles reflow into a single horizontal row instead of a 2×2 grid.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun EmptyEditorStateContent(
    onStartTextBlock: (Uuid) -> Unit,
    onStartPhotoBlock: (Uuid) -> Unit,
    onStartAudioBlock: (Uuid) -> Unit,
    onStartCameraBlock: (Uuid) -> Unit,
    textTileId: Uuid? = null,
    photoTileId: Uuid? = null,
    audioTileId: Uuid? = null,
    cameraTileId: Uuid? = null,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalEditorIsCompact.current

    // Stable IDs pre-generated for each tile; used as the shared element key so
    // the tile morphs into the expanded block surface on tap.
    val textId = remember(textTileId) { textTileId ?: Uuid.random() }
    val audioId = remember(audioTileId) { audioTileId ?: Uuid.random() }
    val cameraId = remember(cameraTileId) { cameraTileId ?: Uuid.random() }
    val photoId = remember(photoTileId) { photoTileId ?: Uuid.random() }

    val sts = LocalSharedTransitionScope.current
    val avs = LocalAnimatedVisibilityScope.current

    if (isCompact) {
        // Landscape / height-constrained: all four tiles in one horizontal row.
        Row(
            modifier =
                modifier
                    .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            TextEntrySurface(
                onClick = { onStartTextBlock(textId) },
                modifier = tileModifier(textId, sts, avs),
            )
            AudioRecordingSurface(
                onClick = { onStartAudioBlock(audioId) },
                modifier = tileModifier(audioId, sts, avs),
            )
            CameraCaptureSurface(
                onClick = { onStartCameraBlock(cameraId) },
                modifier = tileModifier(cameraId, sts, avs),
            )
            PhotoSurface(
                onClick = { onStartPhotoBlock(photoId) },
                modifier = tileModifier(photoId, sts, avs),
            )
        }
    } else {
        // Portrait / normal: 2×2 grid.
        Column(
            modifier =
                modifier
                    .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                TextEntrySurface(
                    onClick = { onStartTextBlock(textId) },
                    modifier = tileModifier(textId, sts, avs),
                )
                AudioRecordingSurface(
                    onClick = { onStartAudioBlock(audioId) },
                    modifier = tileModifier(audioId, sts, avs),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                CameraCaptureSurface(
                    onClick = { onStartCameraBlock(cameraId) },
                    modifier = tileModifier(cameraId, sts, avs),
                )
                PhotoSurface(
                    onClick = { onStartPhotoBlock(photoId) },
                    modifier = tileModifier(photoId, sts, avs),
                )
            }
        }
    }
}

/**
 * Builds the [Modifier] for a picker tile, attaching [SharedTransitionScope.sharedBounds]
 * when the shared transition infrastructure is available.
 */
@Composable
private fun RowScope.tileModifier(
    id: Uuid,
    sts: SharedTransitionScope?,
    avs: AnimatedVisibilityScope?,
): Modifier =
    if (sts != null && avs != null) {
        with(sts) {
            Modifier
                .weight(1f)
                .fillMaxSize()
                .sharedBounds(
                    rememberSharedContentState("block_surface_$id"),
                    animatedVisibilityScope = avs,
                )
        }
    } else {
        Modifier.weight(1f).fillMaxSize()
    }

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TextEntrySurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TextFields,
                    contentDescription = stringResource(Res.string.start_text_entry),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.write_something),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun AudioRecordingSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("editor_start_audio_block"),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = stringResource(Res.string.record_audio),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.record_audio),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CameraCaptureSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(Res.string.capture_photo_or_video),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.capture),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.photo_or_video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PhotoSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = stringResource(Res.string.editor_action_add_photo_gallery),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.gallery),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.choose_a_photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
