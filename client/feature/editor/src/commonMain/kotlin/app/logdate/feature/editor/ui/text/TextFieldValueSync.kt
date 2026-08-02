package app.logdate.feature.editor.ui.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun shouldPropagateTextChange(
    previous: TextFieldValue,
    next: TextFieldValue,
): Boolean = previous.text != next.text

internal fun mergeExternalText(
    current: TextFieldValue,
    externalText: String,
): TextFieldValue {
    if (current.text == externalText) return current

    val selection =
        TextRange(
            start = current.selection.start.coerceIn(0, externalText.length),
            end = current.selection.end.coerceIn(0, externalText.length),
        )
    return TextFieldValue(
        text = externalText,
        selection = selection,
        composition = null,
    )
}
