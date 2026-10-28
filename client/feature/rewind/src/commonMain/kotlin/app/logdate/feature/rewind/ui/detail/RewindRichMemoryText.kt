@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.logdate.ui.common.MarkdownText

/** Rewinds stay dark even when the host theme is light, including headings, code and links. */
@Composable
fun RewindRichMemoryText(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        // The shared reader treats HTML as text and embedded images as alt text.
        MarkdownText(content, modifier, textStyle.copy(color = Color.White))
    }
}
