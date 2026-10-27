@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorStartContextLayout(
    entryContext: (@Composable () -> Unit)?,
    picker: @Composable (Modifier) -> Unit,
) {
    if (entryContext == null) {
        picker(Modifier.fillMaxSize())
        return
    }
    val density = LocalDensity.current
    var contextHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pickerHeight = (maxHeight - with(density) { contextHeight.toDp() }).coerceAtLeast(320.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .widthIn(max = 816.dp)
                    .fillMaxWidth()
                    .onSizeChanged { contextHeight = it.height }
                    .padding(horizontal = 8.dp, vertical = 16.dp),
            ) {
                entryContext()
            }
            picker(Modifier.fillMaxWidth().height(pickerHeight))
        }
    }
}
