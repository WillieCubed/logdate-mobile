package app.logdate.feature.editor.ui.image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.shared.model.PhotoPresentation
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_caption
import org.jetbrains.compose.resources.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun PhotoEditingControls(
    block: ImageBlockUiState,
    onBlockUpdated: (ImageBlockUiState) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val captionColor = if (block.presentation == PhotoPresentation.Framed) Color.Black else colors.onSurface
    val captionStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicTextField(
            value = block.caption,
            onValueChange = { onBlockUpdated(block.copy(caption = it)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            textStyle = captionStyle.copy(color = captionColor),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { input ->
                Box(Modifier.padding(vertical = 8.dp)) {
                    if (block.caption.isEmpty()) {
                        Text(stringResource(Res.string.add_a_caption), style = captionStyle, color = captionColor.copy(alpha = 0.6f))
                    }
                    input()
                }
            },
        )
    }
}
