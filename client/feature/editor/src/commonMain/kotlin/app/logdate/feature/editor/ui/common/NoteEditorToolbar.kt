package app.logdate.feature.editor.ui.newstuff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing

/**
 *
 */
@Composable
fun NoteEditorToolbar(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShowDrafts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom=Spacing.xl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left-aligned back button
        FilledTonalIconButton(
            onClick = { onBack() },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                Spacing.sm,
                alignment = Alignment.End,
            ),
        ) {
            FilledTonalIconButton(
                onClick = {
                    onSave()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save entry"
                )
            }
        }
    }
}