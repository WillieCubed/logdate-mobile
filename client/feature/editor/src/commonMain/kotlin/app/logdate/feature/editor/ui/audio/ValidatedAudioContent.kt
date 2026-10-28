@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.formatMediaDuration
import kotlinx.coroutines.CancellationException
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.audio_preview_checking
import logdate.client.feature.editor.generated.resources.audio_preview_loading
import logdate.client.feature.editor.generated.resources.audio_preview_retry_playback
import logdate.client.feature.editor.generated.resources.audio_preview_unavailable
import logdate.client.feature.editor.generated.resources.audio_preview_unavailable_details
import logdate.client.feature.editor.generated.resources.recording_recovery_retry
import org.jetbrains.compose.resources.stringResource

private data class AudioPreviewResolution(
    val uri: String,
    val durationMs: Long? = null,
    val loading: Boolean = true,
)

@Composable
internal fun ValidatedAudioContent(
    audioUri: String,
    knownDurationMs: Long,
    resolveDurationMs: suspend (String) -> Long?,
    modifier: Modifier = Modifier,
    content: @Composable (Long) -> Unit,
) {
    var retry by remember(audioUri) { mutableIntStateOf(0) }
    val resolved by produceState(AudioPreviewResolution(audioUri), audioUri, retry) {
        value = AudioPreviewResolution(audioUri)
        val duration =
            try {
                if (audioUri.isBlank()) null else resolveDurationMs(audioUri)?.takeIf { it > 0 }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        value = AudioPreviewResolution(audioUri, duration, loading = false)
    }
    val current = resolved.takeIf { it.uri == audioUri } ?: AudioPreviewResolution(audioUri)
    Box(modifier.testTag("audio_preview_container").animateContentSize()) {
        if (!current.loading && current.durationMs != null) {
            Box(Modifier.testTag("audio_preview_ready")) { content(current.durationMs) }
        } else if (current.loading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (retry > 0) Res.string.audio_preview_checking else Res.string.audio_preview_loading),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (knownDurationMs > 0) Text(formatMediaDuration(knownDurationMs, true), style = MaterialTheme.typography.labelLarge)
                CircularProgressIndicator()
            }
        } else {
            AudioUnavailableContent(knownDurationMs, onRetry = { retry++ })
        }
    }
}

@Composable
internal fun AudioUnavailableContent(
    knownDurationMs: Long,
    onRetry: () -> Unit,
    playbackFailure: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.audio_preview_unavailable), style = MaterialTheme.typography.titleMedium)
        if (knownDurationMs > 0) Text(formatMediaDuration(knownDurationMs, true), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(Res.string.audio_preview_unavailable_details), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("audio_preview_retry")) {
            Text(stringResource(if (playbackFailure) Res.string.audio_preview_retry_playback else Res.string.recording_recovery_retry))
        }
    }
}
