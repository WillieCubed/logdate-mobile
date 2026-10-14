@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.recording_session_active
import logdate.client.ui.generated.resources.recording_session_paused
import logdate.client.ui.generated.resources.recording_session_saving
import logdate.client.ui.generated.resources.recording_session_source_elapsed
import logdate.client.ui.generated.resources.recording_session_stop
import logdate.client.ui.generated.resources.recording_session_stop_short
import org.jetbrains.compose.resources.stringResource

/** Shared recording status and save action; device origin is context for the same recording task. */
@Composable
fun RecordingSessionControl(
    source: String,
    elapsedMs: Long,
    paused: Boolean,
    stopping: Boolean,
    error: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seconds = elapsedMs.coerceAtLeast(0) / 1_000
    val duration = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    Surface(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(Modifier.padding(Spacing.md)) {
            val stacked = maxWidth < 256.dp || LocalDensity.current.fontScale >= 1.5f
            val status: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        Modifier.size(8.dp).background(
                            if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                    )
                    Column {
                        Text(
                            stringResource(if (paused) Res.string.recording_session_paused else Res.string.recording_session_active),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(Res.string.recording_session_source_elapsed, source, duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val action: @Composable () -> Unit = {
                RecordingStopButton(
                    label = stringResource(if (stopping) Res.string.recording_session_saving else Res.string.recording_session_stop_short),
                    onStop = onStop,
                    contentDescription = stringResource(Res.string.recording_session_stop),
                    enabled = !stopping,
                    compact = true,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (stacked) {
                    status()
                    action()
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        status()
                        action()
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** The same stop transport used by the phone recorder and remote recording session. */
@Composable
fun RecordingStopButton(
    label: String,
    onStop: () -> Unit,
    contentDescription: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onStop,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        contentPadding = if (compact) PaddingValues(horizontal = Spacing.md) else ButtonDefaults.ContentPadding,
        modifier =
            modifier.then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
    ) {
        Icon(
            painter = PlatformIcons.stop(),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            modifier =
                if (compact) {
                    Modifier.padding(start = Spacing.xs)
                } else {
                    Modifier.padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm)
                },
        )
    }
}
