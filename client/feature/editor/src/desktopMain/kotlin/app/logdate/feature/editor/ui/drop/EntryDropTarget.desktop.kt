package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.common.noteDropTarget

/** Text drops only on this platform; media drops are handled on Android. */
@Composable
actual fun Modifier.entryDropTarget(
    onText: (String) -> Unit,
    onMediaImported: (List<String>) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
): Modifier = noteDropTarget(onText)
