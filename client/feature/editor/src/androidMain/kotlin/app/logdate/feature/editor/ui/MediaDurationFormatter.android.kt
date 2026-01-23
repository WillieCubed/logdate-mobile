package app.logdate.feature.editor.ui

import android.text.format.DateUtils

internal actual fun formatMediaDuration(durationMs: Long, padMinutes: Boolean): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return DateUtils.formatElapsedTime(totalSeconds)
}
