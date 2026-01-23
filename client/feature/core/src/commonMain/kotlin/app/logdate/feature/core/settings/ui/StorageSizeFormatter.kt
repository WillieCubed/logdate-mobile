package app.logdate.feature.core.settings.ui

/**
 * Formats a byte count for display in the settings UI.
 */
internal expect fun formatByteSize(bytes: Long): String
