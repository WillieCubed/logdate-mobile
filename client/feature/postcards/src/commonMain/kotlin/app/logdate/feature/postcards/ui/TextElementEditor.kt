package app.logdate.feature.postcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Available font choices for text elements.
 */
enum class FontChoice(
    val id: String,
    val displayName: String,
) {
    CAVEAT("caveat", "Caveat"),
    DANCING_SCRIPT("dancing-script", "Dancing Script"),
    PATRICK_HAND("patrick-hand", "Patrick Hand"),
}

/**
 * Inline editor for creating or modifying a text element.
 *
 * Shows an action bar (cancel / confirm), a text input field, and a font picker.
 *
 * @param initialText The initial text content.
 * @param initialFont The initial font family ID.
 * @param initialColor The initial text color as a hex string.
 * @param initialFontSize The initial font size.
 * @param onConfirm Called when the user confirms the text, with content, font ID, color, and size.
 * @param onDismiss Called when the user cancels.
 */
@Composable
fun TextElementEditor(
    initialText: String = "",
    initialFont: String = FontChoice.CAVEAT.id,
    initialColor: String = "#333333",
    initialFontSize: Float = 24f,
    onConfirm: (content: String, fontFamily: String, color: String, fontSize: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedFont by remember {
        mutableStateOf(
            FontChoice.entries.find { it.id == initialFont } ?: FontChoice.CAVEAT,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .imePadding()
                .padding(16.dp),
    ) {
        // Action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text, selectedFont.id, initialColor, initialFontSize)
                    }
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Confirm")
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle =
                TextStyle(
                    color = parseColor(initialColor),
                    fontSize = initialFontSize.sp,
                    fontFamily = resolveFontFamily(selectedFont.id),
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
        )

        // Font picker
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontChoice.entries.forEach { font ->
                FilterChip(
                    selected = selectedFont == font,
                    onClick = { selectedFont = font },
                    label = {
                        Text(
                            font.displayName,
                            fontFamily = resolveFontFamily(font.id),
                        )
                    },
                )
            }
        }
    }
}
