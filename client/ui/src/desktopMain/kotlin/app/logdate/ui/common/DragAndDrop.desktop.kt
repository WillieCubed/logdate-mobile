package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual fun Modifier.noteDragSource(text: String): Modifier = this

actual fun Modifier.noteDropTarget(onDrop: (String) -> Unit): Modifier = this

actual fun Modifier.batchDragSource(items: List<String>): Modifier = this

@Composable
actual fun Modifier.mediaDragSource(uri: String): Modifier = noteDragSource(uri)
