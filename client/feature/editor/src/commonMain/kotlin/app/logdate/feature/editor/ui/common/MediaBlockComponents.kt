package app.logdate.feature.editor.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_caption
import org.jetbrains.compose.resources.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
fun DeleteMediaButton(
    onClick: () -> Unit,
    contentDescription: String = "Delete",
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                    shape = CircleShape,
                ),
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun OverlayCaptionField(
    caption: String,
    onCaptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = caption,
        onValueChange = onCaptionChanged,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        decorationBox = { inner ->
            if (caption.isEmpty()) {
                Text(
                    stringResource(Res.string.add_a_caption),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            inner()
        },
        maxLines = 3,
        modifier = modifier.fillMaxWidth(),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun MediaCaptionField(
    caption: String,
    onCaptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = caption,
        onValueChange = onCaptionChanged,
        placeholder = { Text(stringResource(Res.string.add_a_caption)) },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
    )
}
