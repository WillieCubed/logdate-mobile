@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.runtime.Composable
import app.logdate.client.location.tracking.LocationCaptureStatus

internal data class HistoryRecordingAccess(
    val ready: Boolean = false,
    val message: String = "Recording is available on Android. Your saved history is still available here.",
    val actionLabel: String = "Recording unavailable",
    val reducedMotion: Boolean = true,
    val resolve: () -> Unit = {},
    val openSettings: (() -> Unit)? = null,
    val captureStatus: LocationCaptureStatus = LocationCaptureStatus.Stopped,
)

@Composable
internal expect fun rememberHistoryRecordingAccess(): HistoryRecordingAccess
