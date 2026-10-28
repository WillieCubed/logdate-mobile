package app.logdate.feature.editor.ui.camera

import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.ui.media.MediaDeviceSelector

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun CameraRouteSelector(
    selection: MediaDeviceSelectionUiState,
    onDeviceSelected: (String) -> Unit,
    label: String,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    MediaDeviceSelector(
        selection,
        onDeviceSelected,
        label = label,
        enabled = enabled,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = colors.surfaceContainerHigh,
                labelColor = colors.onSurface,
                leadingIconContentColor = colors.onSurfaceVariant,
                disabledContainerColor = colors.surfaceContainerHigh,
            ),
    )
}
