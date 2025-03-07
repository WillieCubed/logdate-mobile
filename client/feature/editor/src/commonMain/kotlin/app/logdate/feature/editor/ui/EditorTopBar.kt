@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.editor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.LocalDate

/**
 * A toolbar with actions for an entry editor.
 *
 * @param date The semantic date of the entry being currently edited.
 * @param onNavigateBack Callback for when the user navigates back.
 */
@Composable
internal fun EditorTopBar(
    date: LocalDate,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = { Text(date.toReadableDateShort()) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Exit editor")
            }
        },
        actions = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        }
    )
}