package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import app.logdate.feature.editor.ui.image.ImageBlockPreview

/** Passive entry context: it has no editor callbacks and cannot change capture ownership. */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun EntryContextPreview(
    blocks: List<EntryBlockUiState>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("editor_passive_context"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(8.dp),
    ) {
        items(blocks, key = { it.id }) { block ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("context_block_${block.id}"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                when (block) {
                    is TextBlockUiState -> Text(block.content, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                    is ImageBlockUiState -> if (block.uri != null) ImageBlockPreview(block, wrapToImage = true)
                    is CameraBlockUiState ->
                        if (block.uri != null) {
                            if (block.mediaType == CapturedMediaType.VIDEO) {
                                Text(
                                    block.caption.ifBlank { memoryBlockLabel(block) },
                                    Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            } else {
                                ImageBlockPreview(ImageBlockUiState(id = block.id, uri = block.uri), wrapToImage = true)
                            }
                        }
                    is AudioBlockUiState ->
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatMediaDuration(block.duration, true), style = MaterialTheme.typography.labelMedium)
                            if (block.transcription.isNotBlank()) {
                                Text(
                                    block.transcription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    is VideoBlockUiState ->
                        Text(
                            block.caption.ifBlank {
                                memoryBlockLabel(block)
                            },
                            Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                }
            }
        }
    }
}
