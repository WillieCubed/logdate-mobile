@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.journals.ui.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.journal.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun JournalCreationScreen(
    onGoBack: () -> Unit,
    onJournalCreated: (journalId: Uuid) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalCreationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()

    JournalCreationScreenContent(
        onGoBack = onGoBack,
        onNewJournal = viewModel::createJournal,
        initialTitle = uiState.title,
        selectedNoteIds = uiState.selectedNoteIds,
        recentNotes = recentNotes,
        onToggleNoteSelection = viewModel::toggleNoteSelection,
        modifier = modifier,
    )

    LaunchedEffect(uiState.journalId) {
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
    selectedNoteIds: Set<Uuid> = emptySet(),
    recentNotes: List<RecentNoteItem> = emptyList(),
    onToggleNoteSelection: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var contentDescription by rememberSaveable { mutableStateOf("") }
    var showNotePicker by rememberSaveable { mutableStateOf(false) }

    val canFinish = title.isNotBlank()
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    fun handleNewJournal() {
        onNewJournal(NewJournalRequest(title, contentDescription))
    }

    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_journal)) },
                navigationIcon = {
                    IconButton(onClick = { onGoBack() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                    value = title,
                    label = { Text(stringResource(Res.string.add_a_title)) },
                    onValueChange = { title = it },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                Column {
                    Text(stringResource(Res.string.what_is_this_for), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(descriptionFocusRequester),
                        value = contentDescription,
                        placeholder = { Text(stringResource(Res.string.description)) },
                        label = { },
                        onValueChange = { contentDescription = it },
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (canFinish) handleNewJournal()
                                },
                            ),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(stringResource(Res.string.add_memories), style = MaterialTheme.typography.labelMedium)
                    ContainerButton(
                        onClick = { /* TODO: Platform media picker via expect/actual */ },
                        icon = {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        },
                        label = stringResource(Res.string.add_media_label),
                        description = stringResource(Res.string.add_media_description),
                    )
                    ContainerButton(
                        onClick = { showNotePicker = true },
                        icon = {
                            if (selectedNoteIds.isNotEmpty()) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            } else {
                                Icon(painterResource(Res.drawable.note_stack_add), contentDescription = null)
                            }
                        },
                        label =
                            if (selectedNoteIds.isNotEmpty()) {
                                stringResource(Res.string.notes_selected, selectedNoteIds.size)
                            } else {
                                stringResource(Res.string.add_text_notes_label)
                            },
                        description = stringResource(Res.string.add_text_notes_description),
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = ::handleNewJournal,
                enabled = canFinish,
            ) {
                Text(stringResource(Res.string.finish))
            }
        }
    }

    if (showNotePicker) {
        NotePickerBottomSheet(
            notes = recentNotes,
            selectedNoteIds = selectedNoteIds,
            onToggleSelection = onToggleNoteSelection,
            onDismiss = { showNotePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePickerBottomSheet(
    notes: List<RecentNoteItem>,
    selectedNoteIds: Set<Uuid>,
    onToggleSelection: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            Text(
                text = stringResource(Res.string.select_notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )

            if (notes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_entries_in_this_journal_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            } else {
                LazyColumn {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Checkbox(
                                checked = note.id in selectedNoteIds,
                                onCheckedChange = { onToggleSelection(note.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = note.timestamp.toReadableDateTimeShort(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
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
        colors =
            ButtonDefaults.buttonColors(
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
