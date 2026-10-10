@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable

/** Keyboards on this platform insert text only, so the editor is shown unchanged. */
@Composable
actual fun EntryKeyboardContentReceiver(
    onMediaImported: (EntryMediaImport) -> Unit,
    content: @Composable () -> Unit,
) = content()
