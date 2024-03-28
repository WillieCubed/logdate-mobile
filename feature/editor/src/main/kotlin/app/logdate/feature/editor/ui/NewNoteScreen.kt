package app.logdate.feature.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.editor.R
import app.logdate.model.UserPlace
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun NewNoteRoute(
    onClose: () -> Unit,
    onNoteSaved: () -> Unit,
    viewModel: NoteCreationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // TODO: Load state components in parallel
    if (state !is NoteCreationUiState.Success) {
        return
    }
    val success = state as NoteCreationUiState.Success
    NoteCreationScreen(
        onClose = onClose,
        onAddNote = { viewModel.addNote(it, onNoteSaved) },
        currentLocation = success.currentLocation,
        onRefreshLocation = { },
    )
}

// TODO: Probably move this to a feature module
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCreationScreen(
    onClose: () -> Unit,
    onAddNote: (note: NewEntryContent) -> Unit,
    onRefreshLocation: () -> Unit,
    currentLocation: UserPlace? = null,
) {
    var showDismissDialog by rememberSaveable { mutableStateOf(false) }
    var noteContent: String by rememberSaveable { mutableStateOf("") }
    var currentDate: Instant by remember { mutableStateOf(Clock.System.now()) }
    // TODO: Allow user to change log time (maybe? depending on desired UX)

    fun handleRecordVideo() {

    }

    fun handleTakePhoto() {

    }

    fun handleRecordVoiceNote() {

    }

    fun handleAddNote() {
        // TODO: Add user location
        onAddNote(NewEntryContent(noteContent))
    }

    val canExitCleanly = noteContent.isEmpty()

    fun closeScreen() {
        if (canExitCleanly) {
            onClose()
        } else {
            showDismissDialog = true
        }
    }

    val allowCreation = noteContent.isNotEmpty()

    BackHandler(enabled = !canExitCleanly) {
        showDismissDialog = true
    }

    if (showDismissDialog) {
        SheetDismissDialog(
            onCancel = { showDismissDialog = false },
            onConfirm = {
                showDismissDialog = false
                onClose()
            },
        )
    }
    Scaffold(modifier = Modifier.padding(bottom = Spacing.lg), topBar = {
        TopAppBar(
            title = { Text(currentDate.toReadableDateShort()) },
            navigationIcon = {
                IconButton(onClick = { closeScreen() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            },
        )
    }) { paddingValues ->
        Column(
            Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column {
                WritingEntryBlock(
                    creationTime = currentDate,
                    entryContents = noteContent,
                    onNoteUpdate = { noteContent = it },
                    location = currentLocation,
                    modifier = Modifier,
                )
                LazyVerticalGrid(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    columns = GridCells.Adaptive(minSize = 172.dp),
                ) {
                    item {
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = ::handleRecordVideo,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = "")
                                Text("Record video")
                            }
                        }
                    }
                    item {
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = ::handleTakePhoto,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "")
                                Text("Add photo")
                            }
                        }
                    }
                    item {
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = ::handleRecordVoiceNote
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Text("New voice note")
                            }
                        }
                    }
                }

            }
            Button(
                enabled = allowCreation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                shape = MaterialTheme.shapes.small,
                onClick = ::handleAddNote,
            ) {
                Text(stringResource(R.string.new_note_action_finish))
            }
        }
    }
}

/**
 * A dialog that informs the user if they have unsaved changes and allows them to dismiss the screen.
 */
@Composable
internal fun SheetDismissDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(R.string.action_note_create_discard_confirmation_title)) },
        text = { Text(stringResource(R.string.action_note_create_discard_confirmation_description)) },
        onDismissRequest = onCancel,
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_note_create_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_note_create_discard))
            }
        },
    )
}

@Preview
@Composable
private fun NewNoteRoutePreview() {
    LogDateTheme {
        NewNoteRoute(onClose = {}, onNoteSaved = { })
    }
}

@Composable
fun WritingEntryBlock(
    creationTime: Instant,
    location: UserPlace?,
    entryContents: String,
    onNoteUpdate: (newContent: String) -> Unit,
    modifier: Modifier = Modifier,
    onLocationClick: () -> Unit = {},
) {
    val expanded by rememberSaveable { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locationText =
        location?.metadata?.name ?: stringResource(R.string.text_placeholder_location)
    Column(
        modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Canvas(
                Modifier
                    .size(16.dp)
                    .padding(top = Spacing.sm),
            ) {
                drawCircle(lineColor, center = center, radius = size.width / 2f)
            }
            Text(creationTime.localTime, style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.weight(1f))
            LocationChip(location = locationText, onClick = onLocationClick)
        }
        EditorField(
            expanded = expanded,
            contents = entryContents,
            onNoteUpdate = onNoteUpdate,
            placeholder = stringResource(R.string.text_placeholder_start_writing)
        )
    }
}

@Composable
fun EditorField(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    contents: String = "",
    placeholder: String = "",
    onNoteUpdate: (newContent: String) -> Unit,
) {
    val edgePadding =
        Spacing.lg // Convenience so that placeholder text is aligned with the text field content
    Box {
        BasicTextField(
            modifier = modifier
                .fillMaxWidth()
                .applyExpandedHeight(expanded)
                .sizeIn(minHeight = 144.dp) // TODO: Handle smaller screen heights
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.medium,
                )
                .padding(edgePadding)
                .imePadding(),
            value = contents,
            onValueChange = onNoteUpdate,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineBreak = LineBreak.Paragraph,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        if (contents.isEmpty()) {
            Text(
                modifier = Modifier.padding(edgePadding),
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun Modifier.applyExpandedHeight(
    expanded: Boolean
): Modifier {
    return if (expanded) {
        then(Modifier.fillMaxHeight())
    } else {
        this
    }
}

@Composable
fun ContainerButton(modifier: Modifier) {
    Row(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {

    }
}

