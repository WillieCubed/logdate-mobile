package app.logdate.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import app.logdate.ui.conditional
import app.logdate.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.EditorField(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    contents: String = "",
    placeholder: String = "",
    onNoteUpdate: (newContent: String) -> Unit,
) {
//    val keyboardOptions = rememberSaveable { KeyboardOptions(
//        capitalization = KeyboardCapitalization.Sentences,
//        autoCorrectEnabled = true,
//        keyboardType = KeyboardType.Text,
//    )}

    // Convenience so that placeholder text is aligned with the text field content
    val edgePadding = Spacing.lg

    Box(
        modifier = modifier
            .conditional(expanded) {
                weight(1f)
            }
            .imePadding()
            .imeNestedScroll()
            .navigationBarsPadding(),
    ) {
        BasicTextField(
            modifier = modifier
//                .height(IntrinsicSize.Max)
                .sizeIn(minHeight = 144.dp) // TODO: Handle smaller screen heights
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.medium,
                )
                .padding(edgePadding),
            value = contents,
            onValueChange = onNoteUpdate,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineBreak = LineBreak.Paragraph,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        if (contents.isEmpty()) {
            Text(
                modifier = Modifier.padding(edgePadding),
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
