package app.logdate.ui.platform

import androidx.compose.ui.platform.ClipEntry

/**
 * A clipboard entry holding [text], for `LocalClipboard.current.setClipEntry`. A [sensitive] entry,
 * such as a recovery phrase, is kept out of clipboard previews where the platform supports it.
 */
expect fun plainTextClipEntry(
    text: String,
    sensitive: Boolean = false,
): ClipEntry
