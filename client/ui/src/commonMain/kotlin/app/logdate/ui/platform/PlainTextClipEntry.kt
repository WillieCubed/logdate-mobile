package app.logdate.ui.platform

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry holding [text], for `LocalClipboard.current.setClipEntry`. */
expect fun plainTextClipEntry(text: String): ClipEntry
