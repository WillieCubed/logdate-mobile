package app.logdate.feature.editor.ui.newstuff

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview


/**
 * A wrapper container for a note editor
 */
@Composable
internal fun NoteEditorSurface(
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .animateContentSize()
            .widthIn(max = 600.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        children()
    }
}

/**
 * An editor for text-based notes.
 *
 * Should be wrapped in a [NoteEditorSurface].
 */
@Composable
internal fun TextNoteEditor(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardOptions = remember {
        KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
        )
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (textFieldState.text.isEmpty()) {
            Text(
                "Write something…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = 16.dp, y = 16.dp),
            )
        }
        BasicTextField(
            modifier = Modifier
                .padding(Spacing.lg)
                .fillMaxSize(),
            enabled = enabled,
            state = textFieldState,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            interactionSource = interactionSource,
            decorator = { innerTextField ->
                innerTextField()
            },
        )
    }
}


@Preview
@Composable
private fun TextNoteEditorPreview() {
    NoteEditorSurface {
        TextNoteEditor(
            textFieldState = TextFieldState()
        )
    }
}

