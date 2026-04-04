package app.logdate.feature.postcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.feature.postcards.model.PostcardDocument
import kotlin.uuid.Uuid

/**
 * Export UI that adapts by screen size:
 * - **Compact (phones):** ModalBottomSheet
 * - **Expanded (tablets, desktop):** AlertDialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    document: PostcardDocument,
    viewModel: ExportViewModel,
    stickerUriMap: Map<Uuid, String> = emptyMap(),
    onShareResult: (uri: String) -> Unit,
    onSaveToFiles: ((uri: String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    val onDismissRequest = {
        viewModel.dismiss()
        onDismiss()
    }

    if (isExpanded) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Export Postcard") },
            text = {
                ExportContent(
                    viewModel = viewModel,
                    document = document,
                    stickerUriMap = stickerUriMap,
                    onShareResult = onShareResult,
                    onSaveToFiles = onSaveToFiles,
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            },
        )
    } else {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Export Postcard", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                ExportContent(
                    viewModel = viewModel,
                    document = document,
                    stickerUriMap = stickerUriMap,
                    onShareResult = onShareResult,
                    onSaveToFiles = onSaveToFiles,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ExportContent(
    viewModel: ExportViewModel,
    document: PostcardDocument,
    stickerUriMap: Map<Uuid, String>,
    onShareResult: (uri: String) -> Unit,
    onSaveToFiles: ((uri: String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is ExportUiState.Idle,
        is ExportUiState.Ready,
        -> {
            val selectedPreset =
                (current as? ExportUiState.Ready)?.preset
                    ?: ExportPreset.STORY

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text("Ready to share", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onShareResult(current.result.uri) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share")
            }
            if (onSaveToFiles != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSaveToFiles(current.result.uri) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save to Files")
                }
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
}
