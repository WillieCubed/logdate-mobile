package app.logdate.feature.journals.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_delete
import logdate.client.feature.journal.generated.resources.delete_journal_description
import logdate.client.feature.journal.generated.resources.delete_journal_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The main screen to view a journal's contents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    viewModel: JournalDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var openDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
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
            Scaffold(
                topBar = {
                    LargeTopAppBar(
                        title = { Text((state as JournalDetailUiState.Success).title) },
                        navigationIcon = {
                            IconButton(onClick = { onGoBack() }) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { openDeleteConfirmation = true }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete journal"
                                )
                            }
                        },
                    )
                },
            ) {
                Column(
                    Modifier
                        .padding(it)
                        .padding(Spacing.lg)
                ) {
                    Text("This is a journal.")
                }
            }
            if (openDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onDismissRequest = { openDeleteConfirmation = false },
                    onConfirmation = {
                        viewModel.deleteJournal(onJournalDeleted)
                        openDeleteConfirmation = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    icon: ImageVector = Icons.Default.WarningAmber,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.delete_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirmation,
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun JournalDetailPlaceholder() {
    Row(
        modifier = Modifier
            .padding(Spacing.lg)
            .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading..."
        )
    }
}
