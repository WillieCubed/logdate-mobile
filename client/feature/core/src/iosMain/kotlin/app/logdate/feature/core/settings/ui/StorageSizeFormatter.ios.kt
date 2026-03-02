package app.logdate.feature.core.settings.ui

import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

internal actual fun formatByteSize(bytes: Long): String {
    val formatter =
        NSByteCountFormatter().apply {
            countStyle = NSByteCountFormatterCountStyleFile
        }
    return formatter.stringFromByteCount(bytes)
}
