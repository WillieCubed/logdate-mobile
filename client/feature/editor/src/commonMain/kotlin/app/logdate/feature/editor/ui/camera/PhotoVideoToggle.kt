package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.photo
import logdate.client.feature.editor.generated.resources.video
import org.jetbrains.compose.resources.stringResource

/**
 * Toggle between photo and video capture modes.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun PhotoVideoToggle(
    currentMode: CaptureMode,
    onModeChanged: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        SegmentedButtonDefaults.colors(
            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = currentMode == CaptureMode.PHOTO,
            onClick = { onModeChanged(CaptureMode.PHOTO) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = colors,
            icon = { SegmentedButtonDefaults.Icon(active = currentMode == CaptureMode.PHOTO) },
            label = { CameraToggleOption(Icons.Default.CameraAlt, stringResource(Res.string.photo)) },
        )

        SegmentedButton(
            selected = currentMode == CaptureMode.VIDEO,
            onClick = { onModeChanged(CaptureMode.VIDEO) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = colors,
            icon = { SegmentedButtonDefaults.Icon(active = currentMode == CaptureMode.VIDEO) },
            label = { CameraToggleOption(Icons.Default.Videocam, stringResource(Res.string.video)) },
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CameraToggleOption(
    icon: ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(label)
    }
}
