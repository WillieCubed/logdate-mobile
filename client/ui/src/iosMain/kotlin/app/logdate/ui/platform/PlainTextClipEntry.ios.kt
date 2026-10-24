package app.logdate.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun plainTextClipEntry(
    text: String,
    sensitive: Boolean,
): ClipEntry = ClipEntry.withPlainText(text)
