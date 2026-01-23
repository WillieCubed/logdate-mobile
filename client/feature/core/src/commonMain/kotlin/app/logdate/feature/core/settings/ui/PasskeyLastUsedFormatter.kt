package app.logdate.feature.core.settings.ui

import kotlinx.datetime.Instant

/**
 * Formats a passkey's last-used timestamp for display in settings.
 */
expect fun formatPasskeyLastUsed(lastUsed: Instant): String
