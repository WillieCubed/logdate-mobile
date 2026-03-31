package app.logdate.feature.postcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.postcards.model.PostcardDocument
import kotlin.uuid.Uuid

/**
 * Bottom sheet for exporting a postcard as a PNG image.
 *
 * Offers preset aspect ratio selection (Story, Square, Portrait) and
 * triggers rendering + sharing.
 *
 * @param document The postcard to export.
 * @param viewModel The export ViewModel managing the export flow.
 * @param onShareResult Called with the exported file URI when ready to share.
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    document: PostcardDocument,
    viewModel: ExportViewModel,
    stickerUriMap: Map<Uuid, String> = emptyMap(),
    onShareResult: (uri: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.dismiss()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Export Postcard",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(16.dp))

            when (val current = state) {
                is ExportUiState.Idle,
                is ExportUiState.Ready,
                -> {
                    val selectedPreset =
                        (current as? ExportUiState.Ready)?.preset
                            ?: ExportPreset.STORY

                    // Preset picker
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExportPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preset == selectedPreset,
                                onClick = { viewModel.selectPreset(preset) },
                                label = { Text(preset.label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.render(document, stickerUriMap) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export & Share")
                    }
                }

                is ExportUiState.Rendering -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Rendering...", style = MaterialTheme.typography.bodyMedium)
                }

                is ExportUiState.Complete -> {
                    Text(
                        "Ready to share",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onShareResult(current.result.uri) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share")
                    }
                }

                is ExportUiState.Failed -> {
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.startExport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Try again")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
