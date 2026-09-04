package app.logdate.ui.audio

/**
 * Formats a playback position or duration as `m:ss`, or `mm:ss` when [padMinutes] is set.
 *
 * The codebase grew seven separate implementations of this, several byte-identical and one
 * per platform via expect/actual purely to reach `String.format`. Kotlin can do it directly, so
 * audio surfaces share this one. Negative input clamps to zero rather than rendering `-1:59`.
 */
fun formatAudioDuration(
    durationMs: Long,
    padMinutes: Boolean = false,
): String {
    val totalSeconds = (durationMs.coerceAtLeast(0)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minutesText = if (padMinutes) minutes.toString().padStart(2, '0') else minutes.toString()
    return "$minutesText:${seconds.toString().padStart(2, '0')}"
}
