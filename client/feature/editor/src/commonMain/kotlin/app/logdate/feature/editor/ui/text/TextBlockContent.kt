package app.logdate.feature.editor.ui.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.whats_on_your_mind
import org.jetbrains.compose.resources.stringResource

/**
 * Content component for text blocks in the editor.
 *
 * @param block The data object containing the text content
 * @param isExpanded Whether the text block is expanded (full editor) or collapsed
 * @param onTextChanged Callback for when text content changes
 * @param onFocused Callback for when this block receives focus
 * @param readOnly Whether the block is read-only
 * @param modifier Modifier for the component
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun TextBlockContent(
    block: TextBlockUiState,
    isExpanded: Boolean = true,
    onTextChanged: (String) -> Unit,
    onFocused: () -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by
        rememberSaveable(block.id.toString(), stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = block.content,
                    selection = TextRange(block.content.length),
                ),
            )
        }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val markdownTransformation =
        remember(typography, colors) {
            InlineMarkdownVisualTransformation { style ->
                when (style) {
                    InlineMarkdownStyle.HEADING_1 -> typography.headlineMedium.toSpanStyle()
                    InlineMarkdownStyle.HEADING_2 -> typography.headlineSmall.toSpanStyle()
                    InlineMarkdownStyle.HEADING_3 -> typography.titleLarge.toSpanStyle()
                    InlineMarkdownStyle.HEADING_4 -> typography.titleMedium.toSpanStyle()
                    InlineMarkdownStyle.HEADING_5 -> typography.titleSmall.toSpanStyle()
                    InlineMarkdownStyle.HEADING_6 -> typography.labelLarge.toSpanStyle()
                    InlineMarkdownStyle.STRONG -> SpanStyle(fontWeight = FontWeight.Bold)
                    InlineMarkdownStyle.EMPHASIS -> SpanStyle(fontStyle = FontStyle.Italic)
                    InlineMarkdownStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    InlineMarkdownStyle.INLINE_CODE,
                    InlineMarkdownStyle.CODE_BLOCK,
                    ->
                        SpanStyle(
                            color = colors.onSurfaceVariant,
                            background = colors.surfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )

                    InlineMarkdownStyle.LINK ->
                        SpanStyle(
                            color = colors.primary,
                            textDecoration = TextDecoration.Underline,
                        )

                    InlineMarkdownStyle.BLOCK_QUOTE ->
                        SpanStyle(
                            color = colors.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )

                    InlineMarkdownStyle.LIST_MARKER ->
                        SpanStyle(
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                }
            }
        }

    // The container for the text field
    Box(
        modifier =
            modifier
                .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .clickable(
                    enabled = !readOnly,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!isExpanded) {
                        onFocused()
                    }
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        // The actual text field - use local state with callback
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val shouldPropagate = shouldPropagateTextChange(fieldValue, newValue)
                fieldValue = newValue
                if (shouldPropagate) {
                    onTextChanged(newValue.text)
                }
            },
            modifier =
                Modifier
                    .widthIn(max = 840.dp)
                    .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .testTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
                    .focusable(enabled = !readOnly)
                    .focusRequester(focusRequester),
            textStyle = typography.bodyLarge.copy(color = colors.onSurface),
            visualTransformation = markdownTransformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            readOnly = readOnly,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(Spacing.md)) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.whats_on_your_mind),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }

    LaunchedEffect(block.content) {
        fieldValue = mergeExternalText(fieldValue, block.content)
    }

    // Request focus when specified
    LaunchedEffect(block.id, isExpanded) {
        if (isExpanded && !readOnly) {
            try {
                focusRequester.requestFocus()
                onFocused()
            } catch (e: Exception) {
                Napier.e("Failed to request focus: ${e.message}", e)
            }
        }
    }
}

const val LOGDATE_EDITOR_TEXT_INPUT_TAG = "editor_text_input"
