package app.logdate.feature.editor.ui

/**
 * Formats a media duration for display within editor UI surfaces.
 *
 * @param durationMs Duration in milliseconds.
 * @param padMinutes Whether the minutes component should be zero-padded.
 */
internal expect fun formatMediaDuration(
    durationMs: Long,
    padMinutes: Boolean,
): String
