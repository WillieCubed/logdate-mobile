package app.logdate.feature.journals.ui.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.note_stack_add
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JournalCreationScreen(
    onGoBack: () -> Unit,
    onJournalCreated: (journalId: kotlin.uuid.Uuid) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalCreationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    JournalCreationScreenContent(
        onGoBack = onGoBack,
        onNewJournal = viewModel::createJournal,
        initialTitle = uiState.title,
        modifier = modifier,
    )

    LaunchedEffect(
        uiState.journalId,
    ) {
        val journalId = uiState.journalId
        if (uiState.created && journalId != null) {
            onJournalCreated(journalId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalCreationScreenContent(
    onGoBack: () -> Unit,
    onNewJournal: (data: NewJournalRequest) -> Unit,
    initialTitle: String = "",
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var contentDescription by rememberSaveable { mutableStateOf("") }

    val canFinish = title.isNotBlank()
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    fun handleNewJournal() {
        onNewJournal(NewJournalRequest(title, contentDescription))
    }

    fun handleSelectMedia() {

    }

    fun handleSelectTextNotes() {

    }
    
    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    value = title,
                    label = { Text("Add a title") },
                    onValueChange = {
                        title = it
                    },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { 
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    ),
                )
                Column {
                    Text("What is this for?", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(descriptionFocusRequester),
                        value = contentDescription,
                        placeholder = { Text("Description") },
                        label = { },
                        onValueChange = {
                            contentDescription = it
                        },
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (canFinish) {
                                    handleNewJournal()
                                }
                            }
                        ),
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
                                painterResource(Res.drawable.note_stack_add),
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
        JournalCreationScreenContent(
            initialTitle = "The Willie Diaries",
            onGoBack = { },
            onNewJournal = { },
            modifier = Modifier,
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