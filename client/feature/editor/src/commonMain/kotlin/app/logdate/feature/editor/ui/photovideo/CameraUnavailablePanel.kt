package app.logdate.feature.editor.ui.photovideo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.camera_capture_unavailable_on_this_platform
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun CameraUnavailablePanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.camera_capture_unavailable_on_this_platform),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
