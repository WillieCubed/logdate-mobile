package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing

/**
 * The main screen to view a journal's contents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    state: JournalDetailUiState,
    onGoBack: () -> Unit,
    onAddContent: () -> Unit,
) {
    when (state) {
        is JournalDetailUiState.Loading -> {
            JournalDetailPlaceholder()
            return
        }

        is JournalDetailUiState.Error -> {
            // TODO: Handle error state
            // TODO: Redirect to home if journal not found
            return
        }

        is JournalDetailUiState.Success -> {
            Scaffold(topBar = {
                LargeTopAppBar(title = { Text(state.title) }, navigationIcon = {
                    IconButton(onClick = { onGoBack() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                })
            }) {
                Column(
                    Modifier
                        .padding(it)
                        .padding(Spacing.lg)
                ) {
                    Text("This is a journal.")
                }
            }
        }
    }
}