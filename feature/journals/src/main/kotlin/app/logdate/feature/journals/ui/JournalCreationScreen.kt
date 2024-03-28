package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.journals.R
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing

@Composable
fun JournalCreationRoute(
    onGoBack: () -> Unit,
    initialTitle: String = "",
    onJournalCreated: (journalId: String) -> Unit,
    viewModel: JournalCreationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    JournalCreationScreen(
        onGoBack = { /*TODO*/ },
        onNewJournal = viewModel::createJournal,
        initialTitle = initialTitle,
    )

    LaunchedEffect(
        uiState.journalId,
    ) {
        if (uiState.created) {
            onJournalCreated(uiState.journalId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalCreationScreen(
    onGoBack: () -> Unit,
    onNewJournal: (data: NewJournalRequest) -> Unit,
    initialTitle: String = "",
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var contentDescription by rememberSaveable { mutableStateOf("") }

    val canFinish = title.isNotBlank()

    fun handleNewJournal() {
        onNewJournal(NewJournalRequest(title, contentDescription))
    }

    fun handleSelectMedia() {

    }

    fun handleSelectTextNotes() {

    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Journal") },
                navigationIcon = {
                    IconButton(onClick = { onGoBack() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {

                OutlinedTextField(
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    label = { Text("Add a title") },
                    onValueChange = {
                        title = it
                    },
                    singleLine = false,
                )
                Column {
                    Text("What is this for?", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        value = contentDescription,
                        placeholder = { Text("Description") },
                        label = { },
                        onValueChange = {
                            contentDescription = it
                        },
                        singleLine = false,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("Add memories", style = MaterialTheme.typography.labelMedium)
                    ContainerButton(
                        onClick = ::handleSelectMedia,
                        icon = {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = ""
                            )
                        },
                        label = "Add media",
                        description = "Select photos and videos from your library"
                    )
                    ContainerButton(
                        onClick = ::handleSelectTextNotes,
                        icon = {
                            Icon(
                                painterResource(R.drawable.note_stack_add),
                                contentDescription = ""
                            )
                        },
                        label = "Add text notes",
                        description = "Add notes you've already written"
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = ::handleNewJournal,
                enabled = canFinish,
            ) {
                Text("Finish")
            }
        }
    }
}

@Preview
@Composable
fun JournalCreationScreenPreview() {
    LogDateTheme {
        JournalCreationScreen(
            initialTitle = "The Willie Diaries",
            onGoBack = { },
            onNewJournal = { },
        )
    }
}

data class NewJournalRequest(
    val title: String,
    val contentDescription: String,
)

@Composable
internal fun ContainerButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(Spacing.lg),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            icon()
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}