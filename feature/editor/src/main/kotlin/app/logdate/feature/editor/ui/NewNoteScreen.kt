package app.logdate.feature.editor.ui

import android.Manifest
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.data.notes.JournalNote
import app.logdate.feature.editor.R
import app.logdate.model.UserPlace
import app.logdate.ui.ButtonType
import app.logdate.ui.FunButton
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parcelize
import java.io.File

@Composable
fun NewNoteRoute(
    onClose: () -> Unit,
    onNoteSaved: () -> Unit,
    viewModel: NoteCreationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    with(state) {
        NoteCreationScreen(
            onClose = onClose,
            previousEntries = recentNotes,
            onAddNote = { viewModel.addNote(it, onNoteSaved) },
            currentLocation = if (locationUiState is LocationUiState.Enabled) {
                locationUiState.currentPlace
            } else {
                null
            },
            onRefreshLocation = viewModel::refreshLocation,
            initialTextContent = initialContent?.text ?: "",
            initialAttachments = initialContent?.media ?: emptyList(),
            onLocationPermissionResult = viewModel::handleLocationPermissionResult,
            userMessage = userMessage,
        )
    }
}

@Parcelize
data class TimestampContainer(
    val timestamp: Long,
) : Parcelable

val InstantSaver = Saver<Instant, Long>(save = { it.toEpochMilliseconds() },
    restore = { Instant.fromEpochMilliseconds(it) })

/**
 * Caches a temp file for the camera to use.
 */
@Composable
internal fun rememberTempFile(): Uri {
    val context = LocalContext.current
    return remember {
        File.createTempFile("logdate_last_photo_", ".jpg", context.cacheDir).toUri()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCreationScreen(
    onClose: () -> Unit,
    previousEntries: List<JournalNote>,
    onAddNote: (note: NewEntryContent) -> Unit,
    onRefreshLocation: () -> Unit,
    onLocationPermissionResult: (granted: Boolean) -> Unit,
    currentLocation: UserPlace? = null,
    initialTextContent: String = "",
    initialAttachments: List<Uri> = emptyList(),
    userMessage: UserMessage? = null,
) {
    var showDismissDialog by rememberSaveable { mutableStateOf(false) }
    var noteContent: String by rememberSaveable { mutableStateOf(initialTextContent) }
    var creationTimestamp: Instant by rememberSaveable(
        stateSaver = InstantSaver,
    ) { mutableStateOf(Clock.System.now()) }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val tempFile = rememberTempFile()
    var lastPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { successful ->
            if (successful) {
                Log.d("NewNoteRoute", "Photo taken: $tempFile")
                lastPhoto = tempFile
            }
        }
    val locationPermissionRequester =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    onLocationPermissionResult(true)
                }

                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Only approximate location access granted.
                    onLocationPermissionResult(true)
                }

                else -> {
                    // No location access granted.
                    onLocationPermissionResult(false)
                }
            }
        }
    val scrollableContainerState = rememberLazyListState()
    val isInDefaultState = noteContent.isEmpty() && lastPhoto == null

    fun ensureLocationPermission() {
        locationPermissionRequester.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    fun handleRecordVideo() {

    }

    fun handleTakePhoto() {
        cameraLauncher.launch(tempFile)
    }

    fun handleRecordVoiceNote() {

    }

    fun handleAddNote() {
        // TODO: Add user location
        onAddNote(NewEntryContent(noteContent, creationTimestamp = creationTimestamp))
    }

    val canExitCleanly = noteContent.isEmpty()

    fun closeScreen() {
        if (canExitCleanly) {
            onClose()
        } else {
            showDismissDialog = true
        }
    }

    fun refreshTimestamp() {
        creationTimestamp = Clock.System.now()
    }

    val allowCreation by remember { derivedStateOf { noteContent.isNotEmpty() } }

    fun transitionToTextEntry() {

    }

    fun transitionToAudioEntry() {

    }

    fun transitionToPhotoEntry() {

    }

    LaunchedEffect(userMessage) {
        if (userMessage == null) return@LaunchedEffect
        scope.launch {
            val actionResult = snackbarHostState.showSnackbar(
                message = userMessage.text,
                actionLabel = userMessage.actionLabel,
                duration = SnackbarDuration.Short,
                withDismissAction = false,
            )
            if (actionResult == SnackbarResult.ActionPerformed) {
                ensureLocationPermission()
            }
        }
    }

    LaunchedEffect(
        currentLocation,
    ) {
        // TODO: Only request on click
        ensureLocationPermission() // Check for permission on start
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(creationTimestamp.toReadableDateShort()) },
                navigationIcon = {
                    IconButton(onClick = { closeScreen() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            state = scrollableContainerState,
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                WritingEntryBlock(
                    creationTime = creationTimestamp,
                    onCreationTimeClick = ::refreshTimestamp,
                    entryContents = noteContent,
                    onNoteUpdate = { noteContent = it },
                    location = currentLocation,
                    locationEnabled = currentLocation != null,
                    modifier = Modifier,
                    onLocationClick = onRefreshLocation,
                    onRequestLocationUpdate = {
                        ensureLocationPermission()
                        onRefreshLocation()
                    },
                )
            }
//            item {
//                LazyRow(
//                    contentPadding = PaddingValues(vertical = Spacing.lg),
//                ) {
//                    if (lastPhoto != null) {
//                        item {
//                            AsyncImage(
//                                lastPhoto,
//                                contentDescription = "Last photo taken",
//                                modifier = Modifier.size(96.dp),
//                            )
//                        }
//                    }
//                }
//            }
            item {
                AnimatedVisibility(visible = isInDefaultState) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {// Actions
                        FunButton(
                            onClick = ::transitionToTextEntry,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Write",
                                Modifier.size(40.dp)
                            )
                        }
                        FunButton(
                            onClick = ::transitionToAudioEntry,
                            type = ButtonType.SECONDARY,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = "Speak",
                                Modifier.size(40.dp)
                            )
                        }
                        FunButton(
                            onClick = ::transitionToPhotoEntry,
                            type = ButtonType.SECONDARY,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.Photo,
                                contentDescription = "Take a photo",
                                Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
            // Only show finish button if there is content
            item {
                AnimatedVisibility(allowCreation) {

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
    }
}

@Preview
@Composable
private fun NewNoteRoutePreview() {
    LogDateTheme {
        NoteCreationScreen(
            onClose = {},
            previousEntries = emptyList(),
            onAddNote = {},
            onRefreshLocation = {},
            onLocationPermissionResult = {},
        )
    }
}

@Composable
fun WritingEntryBlock(
    creationTime: Instant,
    location: UserPlace?,
    entryContents: String,
    onNoteUpdate: (newContent: String) -> Unit,
    onRequestLocationUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    locationEnabled: Boolean = false,
    onLocationClick: () -> Unit = {},
    onCreationTimeClick: () -> Unit,
) {
    val expanded by rememberSaveable { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locationText =
        location?.metadata?.name ?: stringResource(R.string.text_placeholder_location)

    fun handleLocationClick() {
        onRequestLocationUpdate()
    }

    Column(
        modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier.size(16.dp),
            ) {
                drawCircle(lineColor, center = center, radius = size.width / 2f)
            }
            Text(
                creationTime.localTime,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onCreationTimeClick() }
                    .padding(
                        horizontal = Spacing.sm, vertical = Spacing.xs,
                    ),
            )

            Spacer(modifier = Modifier.weight(1f))
            LocationChip(
                location = locationText, enabled = locationEnabled, onClick = ::handleLocationClick
            )
        }
        EditorField(
            expanded = expanded,
            contents = entryContents,
            onNoteUpdate = onNoteUpdate,
            placeholder = stringResource(R.string.text_placeholder_start_writing)
        )
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