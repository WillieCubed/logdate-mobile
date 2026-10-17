@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.runtime.Composable
import app.logdate.client.location.tracking.LocationCaptureStatus
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.history_access_unavailable_action
import logdate.client.feature.location.timeline.generated.resources.history_access_unavailable_message
import org.jetbrains.compose.resources.StringResource

internal data class HistoryRecordingAccess(
    val ready: Boolean = false,
    val message: StringResource = Res.string.history_access_unavailable_message,
    val actionLabel: StringResource = Res.string.history_access_unavailable_action,
    val reducedMotion: Boolean = true,
    val resolve: () -> Unit = {},
    val openSettings: (() -> Unit)? = null,
    val captureStatus: LocationCaptureStatus = LocationCaptureStatus.Stopped,
)

@Composable
internal expect fun rememberHistoryRecordingAccess(): HistoryRecordingAccess
