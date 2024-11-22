package app.logdate.feature.editor.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.logdate.core.data.notes.JournalNote
import app.logdate.feature.editor.ui.newstuff.NoteEditorSurface
import app.logdate.feature.editor.ui.newstuff.NoteEditorToolbar
import app.logdate.feature.editor.ui.newstuff.TextNoteEditor
import app.logdate.model.UserPlace
import app.logdate.ui.conditional
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parcelize
import java.io.File

internal sealed interface EditorInputMode {
    data object Visual : EditorInputMode
    data object Text : EditorInputMode
    data object Audio : EditorInputMode
}

@Stable
internal class NoteEditorState(
    editMode: EditorInputMode,
    var timestamp: Instant,
) {
    var mode: EditorInputMode = editMode
        private set

    fun setMode(newMode: EditorInputMode) {
        // TODO: Use additional logic to cleanup if necessary
        mode = newMode
    }
}

@Composable
internal fun rememberNoteEditorState(
    initialMode: EditorInputMode = EditorInputMode.Text,
    timestamp: Instant = Clock.System.now(),
): NoteEditorState {
    return remember {
        NoteEditorState(
            editMode = initialMode,
            timestamp = timestamp,
        )
    }
}

/**
 * A screen that allows a user to create or edit a note.

 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
internal fun NoteCreationEditScreen(
    onClose: () -> Unit,
    previousEntries: List<JournalNote>,
    onAddNote: (note: NoteEditorUiState) -> Unit,
    onRefreshLocation: () -> Unit,
    onLocationPermissionResult: (granted: Boolean) -> Unit,
    currentLocation: UserPlace? = null,
    initialTextContent: String = "",
    initialAttachments: List<String> = emptyList(),
    userMessage: UserMessage? = null,
) {
    val screenState = rememberNoteEditorState()
    val noteContent = rememberNoteCreationEditState(initialTextContent, initialAttachments)
    val screenScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDismissDialog by rememberSaveable { mutableStateOf(false) }
    val canExitCleanly by remember {
        derivedStateOf {
            noteContent.isEmpty
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

    LaunchedEffect(userMessage) {
        if (userMessage == null) return@LaunchedEffect
        screenScope.launch {
            val actionResult = snackbarHostState.showSnackbar(
                message = userMessage.text,
                actionLabel = userMessage.actionLabel,
                duration = SnackbarDuration.Short,
                withDismissAction = false,
            )
//            if (actionResult == SnackbarResult.ActionPerformed) {
//                ensureLocationPermission()
//            }
        }
    }

    BackHandler(enabled = !canExitCleanly) {
        showDismissDialog = true
    }

    val mediaPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            uris.forEach {
                noteContent.addAttachment(it.toString())
            }
        }

    fun handleAddMedia() {
        mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    fun handleDismiss() {
        if (canExitCleanly) {
            onClose()
        } else {
            showDismissDialog = true
        }
    }

    fun handleSave() {
        // Only save note if there is content
        if (noteContent.isEmpty) {
            return
        }
        onAddNote(
            NoteEditorUiState(
                textContent = noteContent.textFieldState.text.toString(),
                mediaAttachments = noteContent.mediaAttachments,
            )
        )
    }

    fun handleUpdateTimestamp() {
        noteContent.updateTimestamp()
    }

    fun handleFocusMedia() {

    }

    var recentlyCapturedMediaFile by rememberSaveable {
        mutableStateOf<Uri>(Uri.EMPTY)
    }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) {
                Log.d("NoteCreationEditScreen", "Failed to capture image")
                // TODO: Maybe Show image capture error
            }
            noteContent.addAttachment(recentlyCapturedMediaFile.toString())
        }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val hasCameraPermission by remember {
        derivedStateOf { cameraPermissionState.status.isGranted }
    }

    val context = LocalContext.current

    fun handleOpenCamera() {
        if (!hasCameraPermission) {
            cameraPermissionState.launchPermissionRequest()
            // TODO: Correctly handle permission request result
            cameraLauncher.launch(recentlyCapturedMediaFile)
            return
        }
        // TODO: Make sure this is ready for Kotlin Multiplatform
        val tempCaptureFile = File.createTempFile(
            "logdate_capture",
            ".jpg",
            context.externalCacheDir
        )
        Log.d("NoteCreationEditScreen", "Temp file: ${tempCaptureFile.absolutePath}")
        recentlyCapturedMediaFile = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempCaptureFile,
        )
        cameraLauncher.launch(recentlyCapturedMediaFile)
    }

    // Render composables

    if (showDismissDialog) {
        SheetDismissDialog(
            onCancel = { showDismissDialog = false },
            onConfirm = {
                showDismissDialog = false
                onClose()
            },
        )
    }

    // TODO: Add responsive layout
    // TODO: Round off corners if not full screen
    NoteCreationEditPanel(
//        modifier = Modifier.padding(Spacing.md),
        mode = screenState.mode,
        noteCreationEditState = noteContent,
        locationEnabled = currentLocation != null,
        onClose = ::handleDismiss,
        onFinish = ::handleSave,
        onAddImage = ::handleAddMedia,
        onOpenCamera = ::handleOpenCamera,
        onRemoveMedia = {
            noteContent.removeAttachment(it)
        },
        onFocusImage = {},
        onUpdateTimestamp = ::handleUpdateTimestamp,
    )
}

@Composable
internal fun rememberNoteCreationEditState(
    initialTextContent: String = "",
    initialAttachments: List<String> = emptyList(),
): NoteCreationEditState {
    val textEditorState = rememberTextFieldState(initialTextContent)
    var mediaAttachments by rememberSaveable {
        mutableStateOf(initialAttachments)
    }
    val timestamp by rememberSaveable(stateSaver = InstantSaver) {
        mutableStateOf(Clock.System.now())
    }

    return remember(
        textEditorState,
        mediaAttachments,
    ) {
        NoteCreationEditState(
            textFieldState = textEditorState,
            timestamp = timestamp,
            initialMediaAttachments = mediaAttachments,
            onUpdateAttachments = {
                mediaAttachments = it
            },
        )
    }
}

/*
The note editor
Notes constitute an entry. A day have one or more entries associated with it.
Notes can be reordered and reorganized within an entry.

Multiple consecutive notes within a related context can be grouped together into
a single entry, such as text

The experience is similar to a group chat or a thread on a microblogging
service: each note is a message that can exist independently of the rest, but
notes can be chained together to form a longer narrative.
 */

// A NEW EDITOR
@Stable
internal class EditorState() {

    fun addBlock() {

    }

    fun removeBlock() {

    }
}

@Composable
internal fun rememberEditorState(): EditorState {
    var blocks by rememberSaveable {
        mutableStateOf<List<String>>(emptyList())
    }
    return remember {
        EditorState()
    }
}

/**
 * A state holder for the note creation/edit screen.
 */
@Stable
internal class NoteCreationEditState(
    val textFieldState: TextFieldState,
    timestamp: Instant,
    initialMediaAttachments: List<String> = emptyList(),
    private val onUpdateAttachments: (List<String>) -> Unit,
) {
    /**
     * The media attachments associated with the note.
     */
    private val _mediaAttachments: MutableList<String> = initialMediaAttachments.toMutableList()

    val mediaAttachments: List<String>
        get() = _mediaAttachments

    /**
     * Returns true if there is no content in the editor.
     */
    val isEmpty: Boolean
        get() = textFieldState.text.isEmpty() && mediaAttachments.isEmpty()

    var timestamp: Instant = timestamp
        private set

    /**
     * Updates the timestamp to the current time.
     */
    fun updateTimestamp() {
        // TODO: Debug this
        timestamp = Clock.System.now()
    }

    fun updateTimestamp(newTimestamp: Instant) {
        timestamp = newTimestamp
    }

    fun addAttachment(uri: String) {
        _mediaAttachments.add(uri)
        onUpdateAttachments(_mediaAttachments)
    }

    fun removeAttachment(uri: String) {
        _mediaAttachments.remove(uri)
        onUpdateAttachments(_mediaAttachments)
    }
}

@Parcelize
data class TimestampContainer(
    val timestamp: Long,
) : Parcelable

val InstantSaver = Saver<Instant, Long>(save = { it.toEpochMilliseconds() },
    restore = { Instant.fromEpochMilliseconds(it) })

@Composable
internal fun RowEntryTimeSeparator(
    timestamp: Instant,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val circleColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(16.dp)) {
            drawCircle(
                color = circleColor,
            )
        }
        // Display time
        Text(timestamp.localTime, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A panel that allows the user to create or edit a note.
 */
@Composable
internal fun NoteCreationEditPanel(
    modifier: Modifier = Modifier,
    noteCreationEditState: NoteCreationEditState,
    mode: EditorInputMode = EditorInputMode.Text,
    locationEnabled: Boolean,
    onUpdateTimestamp: () -> Unit,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    onAddImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onRemoveMedia: (uri: String) -> Unit,
    onFocusImage: (uri: String) -> Unit,
) {
    // If screen state media editing mode, use dark background
    val background = if (mode == EditorInputMode.Visual) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val context = LocalContext.current
    Scaffold(
        containerColor = background,
        topBar = {
            NoteEditorToolbar(
                onClose = onClose,
                onFinish = onFinish,
                timestamp = noteCreationEditState.timestamp,
                onTimestampClick = onUpdateTimestamp,
            )
        },
        snackbarHost = {
            // TODO: Display messages
        },
    ) { contentPadding ->
        Column(
            modifier
                .padding(contentPadding)
                .imePadding()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()), // TODO: Support stream of notes
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
//            RowEntryTimeSeparator(
//                timestamp = noteCreationEditState.timestamp,
//                onClick = onUpdateTimestamp,
//            )
            NoteEditorSurface(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.sm)
            ) {
                TextNoteEditor(
                    textFieldState = noteCreationEditState.textFieldState,
                )
            }
            NewNoteMediaCarousel(
                mediaItems = noteCreationEditState.mediaAttachments,
                onItemClicked = onFocusImage,
                onRemoveItem = onRemoveMedia,
                modifier = Modifier
                    .conditional(noteCreationEditState.mediaAttachments.isNotEmpty()) {
                        height(120.dp)
                    }
                    .animateContentSize(),
            )
            Row(
                modifier = Modifier.padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AddImageNoteButton(onAdd = onAddImage, modifier = Modifier.weight(1f))
                val isInPreviewMode = LocalView.current.isInEditMode
                if (context.hasCamera() || isInPreviewMode) {
                    CaptureMediaButton(onClick = onOpenCamera)
                }
            }
        }
    }
}

/**
 * Returns `true` if the device has a camera.
 */
private fun Context.hasCamera() = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

@Composable
private fun AddImageNoteButton(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onAdd, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Photo, null)
            Text("Add media")
        }
    }
}

@Composable
private fun CaptureMediaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showExpanded: Boolean = false,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AddAPhoto, null)
            if (showExpanded) {
                Text("Open camera")
            }
        }
    }
}


internal data class NoteEditorUiState(
    val textContent: String,
    val mediaAttachments: List<String>,
    val timestamp: Instant = Clock.System.now(),
)

internal fun NoteEditorUiState.toNewEntryContent() = NewEntryContent(
    textContent = textContent,
    mediaAttachments = mediaAttachments,
    creationTimestamp = timestamp,
)


@Preview
@Composable
private fun NoteCreationEditPanelPreview() {
    LogDateTheme {
        NoteCreationEditPanel(
            noteCreationEditState = rememberNoteCreationEditState(),
            locationEnabled = true,
            onUpdateTimestamp = {},
            onClose = {},
            onFinish = {},
            onAddImage = {},
            onOpenCamera = {},
            onRemoveMedia = {},
            onFocusImage = {},
        )
    }
}

@Preview
@Composable
private fun NoteCreationEditPanelPreview_WithMedia() {
    val mediaState = rememberNoteCreationEditState(
        initialAttachments = listOf(
            "content://media/external/images/media/1",
            "content://media/external/images/media/2",
            "content://media/external/images/media/3",
        )
    )
    LogDateTheme {
        NoteCreationEditPanel(
            noteCreationEditState = mediaState,
            locationEnabled = true,
            onUpdateTimestamp = {},
            onClose = {},
            onFinish = {},
            onAddImage = {},
            onOpenCamera = {},
            onRemoveMedia = {},
            onFocusImage = {},
        )
    }
}