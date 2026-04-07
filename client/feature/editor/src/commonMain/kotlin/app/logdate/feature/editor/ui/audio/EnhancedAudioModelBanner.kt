package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.audio_models_banner_action_download
import logdate.client.feature.editor.generated.resources.audio_models_banner_action_retry
import logdate.client.feature.editor.generated.resources.audio_models_banner_body
import logdate.client.feature.editor.generated.resources.audio_models_banner_downloading
import logdate.client.feature.editor.generated.resources.audio_models_banner_extracting
import logdate.client.feature.editor.generated.resources.audio_models_banner_failed_corrupt
import logdate.client.feature.editor.generated.resources.audio_models_banner_failed_no_network
import logdate.client.feature.editor.generated.resources.audio_models_banner_failed_server
import logdate.client.feature.editor.generated.resources.audio_models_banner_failed_storage
import logdate.client.feature.editor.generated.resources.audio_models_banner_failed_unknown
import logdate.client.feature.editor.generated.resources.audio_models_banner_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Banner shown above the audio recording controls when the on-device
 * enhanced audio models (Whisper for transcript refinement, CED for ambient
 * sound tagging) aren't downloaded yet, are mid-download, or last failed.
 *
 * Hidden entirely when both models are present, since the editor already
 * works without the banner taking space.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun EnhancedAudioModelBanner(
    status: EnhancedAudioModelStatus,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = status !is EnhancedAudioModelStatus.Ready,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.audio_models_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                BannerBody(status = status)

                BannerAction(
                    status = status,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BannerBody(status: EnhancedAudioModelStatus) {
    when (status) {
        EnhancedAudioModelStatus.Ready -> Unit
        EnhancedAudioModelStatus.NotDownloaded -> {
            Text(
                text = stringResource(Res.string.audio_models_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is EnhancedAudioModelStatus.Downloading -> DownloadingBody(status)
        is EnhancedAudioModelStatus.Failed -> {
            Text(
                text = stringResource(failureMessageFor(status.reason)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DownloadingBody(downloading: EnhancedAudioModelStatus.Downloading) {
    Text(
        text = stringResource(Res.string.audio_models_banner_downloading),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    val fraction = downloading.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // Indeterminate either because Content-Length was unknown or because
        // we're in the brief extraction phase. Same visual either way.
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(Res.string.audio_models_banner_extracting),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BannerAction(
    status: EnhancedAudioModelStatus,
    onDownload: () -> Unit,
) {
    val label =
        when (status) {
            EnhancedAudioModelStatus.NotDownloaded ->
                stringResource(Res.string.audio_models_banner_action_download)
            is EnhancedAudioModelStatus.Failed ->
                stringResource(Res.string.audio_models_banner_action_retry)
            else -> return // No action button while downloading or ready
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDownload) {
            Text(text = label)
        }
    }
}

/**
 * Maps a structured download failure reason to the localized message the
 * banner should show. Centralized so the same string lookup is reused
 * wherever a download status surfaces in UI.
 */
private fun failureMessageFor(reason: ModelDownloadStatus): StringResource =
    when (reason) {
        ModelDownloadStatus.NoNetwork -> Res.string.audio_models_banner_failed_no_network
        ModelDownloadStatus.ServerUnavailable -> Res.string.audio_models_banner_failed_server
        ModelDownloadStatus.OutOfStorage -> Res.string.audio_models_banner_failed_storage
        ModelDownloadStatus.ArchiveCorrupt -> Res.string.audio_models_banner_failed_corrupt
        else -> Res.string.audio_models_banner_failed_unknown
    }
