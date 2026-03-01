package app.logdate.feature.core.settings.ui.devices

import kotlin.time.Instant

/**
 * Formats a device's last-active timestamp for display in settings.
 */
expect fun formatDeviceLastActive(timestamp: Instant): String
