package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Text pastes only on this platform, handled by the text field; pasted media is handled on Android. */
@Composable
actual fun Modifier.entryPasteTarget(onMediaImported: (EntryMediaImport) -> Unit): Modifier = this
