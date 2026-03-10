package app.logdate.ui.common

import androidx.compose.ui.Modifier

actual fun Modifier.noteDragSource(text: String): Modifier = this

actual fun Modifier.noteDropTarget(onDrop: (String) -> Unit): Modifier = this
