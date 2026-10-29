package app.logdate.feature.editor.ui.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalEditorInputFocusBookmarks
import app.logdate.feature.editor.ui.editor.LocalEditorInputTokens
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.common.handwritingDelegate
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
    requestEditingFocus: Boolean = isExpanded,
    focusRequestKey: Int = 0,
    minEditorHeight: Dp = 0.dp,
) {
    val focusRequester = remember { FocusRequester() }
    val inputToken = LocalEditorInputTokens.current[block.id]
    val textSession = LocalEditorTextInputSession.current
    val ownerKey = "${textSession?.runtimeId}:${block.id}:$inputToken"
    var acceptedLocalRevision by rememberSaveable(ownerKey, stateSaver = ownedTextInputSaver(ownerKey, autoSaver<Long>(), Long.MIN_VALUE)) {
        mutableStateOf(Long.MIN_VALUE)
    }
    val initialValue = TextFieldValue(text = block.content, selection = TextRange(block.content.length))
    var fieldValue by
        rememberSaveable(ownerKey, stateSaver = ownedTextInputSaver(ownerKey, TextFieldValue.Saver, initialValue)) {
            mutableStateOf(initialValue)
        }
    var lastFocusedValue by rememberSaveable(ownerKey, stateSaver = ownedTextInputSaver(ownerKey, TextFieldValue.Saver, fieldValue)) {
        mutableStateOf(fieldValue)
    }
    var hasEditingFocus by remember { mutableStateOf(false) }
    val focusBookmarks = LocalEditorInputFocusBookmarks.current
    DisposableEffect(focusBookmarks, block.id, inputToken) {
        val owner = Any()
        focusBookmarks?.attach(block.id, owner) { lastFocusedValue = fieldValue }
        onDispose { focusBookmarks?.detach(block.id, owner) }
    }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val markdownTransformation = rememberInlineMarkdownVisualTransformation()

    // The container for the text field
    BoxWithConstraints(
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
        val verticalPadding = if (isExpanded && maxHeight < 96.dp) Spacing.xs else Spacing.lg
        // The actual text field - use local state with callback
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val shouldPropagate = shouldPropagateTextChange(fieldValue, newValue)
                if (!shouldPropagate || textSession == null) {
                    fieldValue = newValue
                    if (shouldPropagate) onTextChanged(newValue.text)
                } else {
                    val accepted = textSession.commit(block.id, inputToken, newValue.text)
                    if (accepted != null) {
                        acceptedLocalRevision = accepted
                        fieldValue = newValue
                    }
                }
            },
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .heightIn(min = minEditorHeight)
                    .testTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
                    .handwritingDelegate()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        hasEditingFocus = it.isFocused
                        if (it.isFocused) onFocused()
                    },
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
                Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = verticalPadding)) {
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

    LaunchedEffect(block.id, inputToken, block.content, textSession?.renderedRevision) {
        fieldValue =
            if (textSession == null) {
                mergeExternalText(fieldValue, block.content)
            } else {
                mergeAcknowledgedText(fieldValue, block.content, textSession.renderedRevision, acceptedLocalRevision)
            }
    }

    // Request focus when specified
    LaunchedEffect(block.id, inputToken, requestEditingFocus, focusRequestKey) {
        if (requestEditingFocus && !readOnly) {
            try {
                // Done hides the keyboard; returning through the surface should restore selection.
                // A direct tap into an already focused inline field keeps its new cursor instead.
                val restoreSelection = !hasEditingFocus && lastFocusedValue.text == fieldValue.text
                focusRequester.requestFocus()
                if (restoreSelection) fieldValue = fieldValue.copy(selection = lastFocusedValue.selection)
                onFocused()
            } catch (e: Exception) {
                Napier.e("Failed to request focus: ${e.message}", e)
            }
        }
    }
}

const val LOGDATE_EDITOR_TEXT_INPUT_TAG = "editor_text_input"
