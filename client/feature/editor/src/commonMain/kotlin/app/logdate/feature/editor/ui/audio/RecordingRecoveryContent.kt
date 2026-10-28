@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.recording_recovery_kept
import logdate.client.feature.editor.generated.resources.recording_recovery_retry
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RecordingRecoveryContent(
    failure: AudioCaptureState.Failed,
    onRetry: () -> Unit,
    recovering: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(failure.reason), color = MaterialTheme.colorScheme.error)
        Text(stringResource(Res.string.recording_recovery_kept))
        Button(onClick = onRetry, enabled = !recovering, modifier = Modifier.testTag("audio_recovery_retry")) {
            Text(stringResource(Res.string.recording_recovery_retry))
        }
    }
}
