package app.logdate.feature.editor.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeleteMediaButton(
    onClick: () -> Unit,
    contentDescription: String = "Delete",
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .background(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

@Composable
fun MediaCaptionField(
    caption: String,
    onCaptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = caption,
        onValueChange = onCaptionChanged,
        placeholder = { Text("Add a caption...") },
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        maxLines = 3
    )
}
