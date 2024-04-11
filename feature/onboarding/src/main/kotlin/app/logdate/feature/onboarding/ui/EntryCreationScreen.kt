package app.logdate.feature.onboarding.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.camera.LensDirection
import app.logdate.core.camera.LiveCameraPreview
import app.logdate.feature.onboarding.editor.AudioPreviewData
import app.logdate.feature.onboarding.editor.LiveAudioPreview
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.conditional
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class EntryCompositionState {
    INITIAL, TEXT_OPEN, AUDIO_OPEN, PHOTO_OPEN;

    val isExpanded
        get() = this != INITIAL
}

@ExperimentalPermissionsApi
data class PermissionStateHolder(
    val audioPermissionState: PermissionState,
    val photoPermissionState: PermissionState,
) {
    val audioPermissionGranted
        get() = audioPermissionState.status == PermissionStatus.Granted

    val cameraPermissionGranted
        get() = photoPermissionState.status == PermissionStatus.Granted
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EntryCreationScreenWrapper(
    useCompactLayout: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionStateHolder = PermissionStateHolder(
        audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO),
        photoPermissionState = rememberPermissionState(Manifest.permission.CAMERA),
    )

    val uiState by viewModel.uiState.collectAsState()

    EntryCreationScreen(
        useCompactLayout = useCompactLayout,
        onBack = onBack,
        onNext = onNext,
        audioPreviewData = AudioPreviewData(
            currentText = uiState.newEntryData.transcribedSpeech,
            currentDuration = 0,
            isPlaying = uiState.newEntryData.isRecordingAudio,
            canUseAudio = permissionStateHolder.audioPermissionGranted,
        ),
        permissionStateHolder = permissionStateHolder,
        onStopRecordingAudio = viewModel::stopRecordingAudio,
        onStartRecordingAudio = {
//            viewModel.startRecordingAudio()
        },
        entryIsCreated = uiState.entrySubmitted,
        onCreateEntry = { viewModel.addEntry(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EntryCreationScreen(
    useCompactLayout: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCreateEntry: (
        entryContent: NewEntryData
    ) -> Unit,
    entryIsCreated: Boolean = false,
    permissionStateHolder: PermissionStateHolder,
    audioPreviewData: AudioPreviewData,
    onStopRecordingAudio: () -> Unit = {},
    onStartRecordingAudio: () -> Unit = {},
    initialState: EntryCompositionState = EntryCompositionState.INITIAL,
) {
    var state by remember { mutableStateOf(initialState) }
    val time by remember { mutableStateOf(Clock.System.now()) }

    val title = when (state) {
        EntryCompositionState.INITIAL -> "Let's try now."
        EntryCompositionState.TEXT_OPEN -> "Write something"
        EntryCompositionState.AUDIO_OPEN -> "Record voice memo"
        EntryCompositionState.PHOTO_OPEN -> "Take a photo"
    }

    fun handleBack() {
        when (state) {
            EntryCompositionState.INITIAL -> onBack()
            EntryCompositionState.TEXT_OPEN -> state = EntryCompositionState.INITIAL
            EntryCompositionState.AUDIO_OPEN -> state = EntryCompositionState.INITIAL
            EntryCompositionState.PHOTO_OPEN -> state = EntryCompositionState.INITIAL
        }
    }

    LaunchedEffect(
        entryIsCreated
    ) {
        if (entryIsCreated) {
            onNext()
        }
    }

    BackHandler {
        handleBack()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        supplementalContent = {
            Scaffold(
                topBar = {
                    LargeTopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = ::handleBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors().copy(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) { contentPadding ->
                Column(
                    Modifier
                        .padding(contentPadding)
                        .padding(Spacing.lg),
                ) {
                    when (state) {
                        EntryCompositionState.INITIAL -> {
                            Text("Write something, talk about something, or take a photo.")
                        }

                        EntryCompositionState.TEXT_OPEN -> {
                        }

                        EntryCompositionState.AUDIO_OPEN -> {
                        }

                        EntryCompositionState.PHOTO_OPEN -> {
                        }
                    }
                }
            }
        },
        mainContent = {
            if (useCompactLayout) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AnimatedContent(
                            state,
                            transitionSpec = {
                                fadeIn(
                                    animationSpec = tween(3000)
                                ) togetherWith fadeOut(animationSpec = tween(3000))
                            },
                            label = "App Bar",
                        ) { targetState ->
                            when (targetState) {
                                EntryCompositionState.INITIAL -> {
                                    LargeTopAppBar(
                                        title = { Text(title) },
                                        navigationIcon = {
                                            IconButton(onClick = ::handleBack) {
                                                Icon(
                                                    Icons.AutoMirrored.Default.ArrowBack,
                                                    contentDescription = "Back"
                                                )
                                            }
                                        },
                                        scrollBehavior = scrollBehavior,
                                    )
                                }

                                EntryCompositionState.TEXT_OPEN,
                                EntryCompositionState.AUDIO_OPEN,
                                EntryCompositionState.PHOTO_OPEN -> {
                                    TopAppBar(
                                        title = { Text(title) },
                                        navigationIcon = {
                                            IconButton(onClick = ::handleBack) {
                                                Icon(
                                                    Icons.AutoMirrored.Default.ArrowBack,
                                                    contentDescription = "Back"
                                                )
                                            }
                                        },
                                        scrollBehavior = scrollBehavior,
                                    )
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    EntryCreationContent(
                        entryCompositionState = state,
                        onUpdateEntryCompositionState = { state = it },
                        entryRecordTime = time,
                        title = title,
                        permissionState = permissionStateHolder,
                        audioPreviewData = audioPreviewData,
                        onStopRecordingAudio = onStopRecordingAudio,
                        onStartRecordingAudio = onStartRecordingAudio,
                        onCreateEntry = onCreateEntry,
                        onBack = ::handleBack,
                        modifier = Modifier
                            .padding(contentPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    )
                }
            } else {
                // Just show content; app bar is in the supplemental content
                EntryCreationContent(
                    entryCompositionState = state,
                    onUpdateEntryCompositionState = { state = it },
                    entryRecordTime = time,
                    title = title,
                    permissionState = permissionStateHolder,
                    audioPreviewData = audioPreviewData,
                    onStopRecordingAudio = onStopRecordingAudio,
                    onStartRecordingAudio = onStartRecordingAudio,
                    onCreateEntry = onCreateEntry,
                    onBack = ::handleBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
        },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun EntryCreationContent(
    entryCompositionState: EntryCompositionState,
    audioPreviewData: AudioPreviewData,
    onUpdateEntryCompositionState: (EntryCompositionState) -> Unit,
    entryRecordTime: Instant,
    title: String,
    permissionState: PermissionStateHolder,
    onStartRecordingAudio: () -> Unit,
    onStopRecordingAudio: () -> Unit,
    onCreateEntry: (
        entryContent: NewEntryData,
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var textContent by rememberSaveable { mutableStateOf("") }
    val isInDefaultState by remember {
        derivedStateOf {
            entryCompositionState == EntryCompositionState.INITIAL
        }
    }

    fun transitionToTextEntry() {
        onUpdateEntryCompositionState(EntryCompositionState.TEXT_OPEN)
    }

    fun transitionToAudioEntry() {
        permissionState.audioPermissionState.launchPermissionRequest()
        if (permissionState.audioPermissionState.status == PermissionStatus.Granted) {
            onUpdateEntryCompositionState(EntryCompositionState.AUDIO_OPEN)
            // TODO: Add delay to allow for transition
            onStartRecordingAudio()
        } else {
            // TODO: Inform user
        }
    }

    fun delayTransition() {


    }

    fun transitionToPhotoEntry() {
        permissionState.photoPermissionState.launchPermissionRequest()
        if (permissionState.photoPermissionState.status == PermissionStatus.Granted) {
            onStartRecordingAudio()
            onUpdateEntryCompositionState(EntryCompositionState.PHOTO_OPEN)
        } else {
            // TODO: Inform user
        }
    }

    fun handleCreateEntry() {
        onCreateEntry(
            NewEntryData(
                recordedTimestamp = entryRecordTime,
                textContent = textContent,
            )
        )
    }

    val canContinue by remember {
        derivedStateOf {
            textContent.isNotBlank()
//            when (entryCompositionState) {
//                EntryCompositionState.TEXT_OPEN -> {
//                    textContent.isNotBlank()
//                }
//
//                EntryCompositionState.AUDIO_OPEN -> audioPreviewData.canUseAudio
//                EntryCompositionState.PHOTO_OPEN -> permissionState.cameraPermissionGranted
//                // TODO: Validate that photo was taken
//                EntryCompositionState.INITIAL -> false
//            }
        }
    }

    Column(
        modifier
            .padding(vertical = Spacing.lg)
            .conditional(!isInDefaultState) {
                fillMaxHeight()
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg)) {
            EntryPrompt("How is your day going? What stood out to you?")
        }
        Column(
            Modifier
                .padding(horizontal = Spacing.lg)
                .widthIn(max = 444.dp)
                .conditional(entryCompositionState == EntryCompositionState.TEXT_OPEN) {
                    fillMaxHeight()
                }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.lg)
            ) {
                Text(entryRecordTime.localTime, style = MaterialTheme.typography.titleMedium)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                UserContentWindow(
                    entryCompositionState = entryCompositionState,
                    audioPreviewData = audioPreviewData,
                    permissionState = permissionState,
                    onStartRecordingAudio = onStartRecordingAudio,
                    onRestartRecordingAudio = onStartRecordingAudio,
                    onStopRecordingAudio = onStopRecordingAudio,
                    onCapturePhoto = {},
                    textContent = textContent,
                    onTextContentChange = { textContent = it },
                )
                AnimatedVisibility(visible = isInDefaultState) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                AnimatedVisibility(visible = entryCompositionState == EntryCompositionState.TEXT_OPEN) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = ::handleCreateEntry,
                            enabled = canContinue,
                        ) {
                            Text("Continue")
                        }
                    }
                }
                AnimatedVisibility(visible = entryCompositionState == EntryCompositionState.AUDIO_OPEN) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = ::handleCreateEntry,
                            enabled = canContinue,
                        ) {
                            Text("Continue")
                        }
                    }
                }
                AnimatedVisibility(visible = entryCompositionState == EntryCompositionState.PHOTO_OPEN) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FloatingActionButton(
                            onClick = ::handleCreateEntry,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                            ),
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
internal fun ColumnScope.UserContentWindow(
    // TODO: Bundle all this crap into a state object
    entryCompositionState: EntryCompositionState,
    textContent: String = "",
    onTextContentChange: (String) -> Unit,
    audioPreviewData: AudioPreviewData,
    permissionState: PermissionStateHolder,
    onStartRecordingAudio: () -> Unit,
    onRestartRecordingAudio: () -> Unit,
    onStopRecordingAudio: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .conditional(entryCompositionState == EntryCompositionState.TEXT_OPEN) {
                fillMaxHeight()
            }
            .padding(Spacing.lg)
            .heightIn(min = 144.dp)
            .conditional(entryCompositionState.isExpanded) {
                weight(1f)
            }
            .animateContentSize()
            .fillMaxWidth(),
    ) {
        when (entryCompositionState) {
            EntryCompositionState.TEXT_OPEN -> {
                BasicTextField(
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    value = textContent,
                    onValueChange = {
                        onTextContentChange(it)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = false,
                )
            }

            EntryCompositionState.AUDIO_OPEN -> {
                LiveAudioPreview(
                    audioPreviewData = audioPreviewData,
                    showWaveform = true,
                )
            }

            EntryCompositionState.PHOTO_OPEN -> {
                LiveCameraPreview(
                    canUseCamera = permissionState.cameraPermissionGranted,
                    lensDirection = LensDirection.BACK,
                )
            }

            EntryCompositionState.INITIAL -> {
                // Shouldn't render anyway
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private val DUMMY_PERMISSION_STATE_HOLDER = PermissionStateHolder(
    audioPermissionState = object : PermissionState {
        override val permission: String
            get() = Manifest.permission.RECORD_AUDIO
        override val status: PermissionStatus
            get() = PermissionStatus.Granted

        override fun launchPermissionRequest() {}
    },
    photoPermissionState = object : PermissionState {
        override val permission: String
            get() = Manifest.permission.CAMERA
        override val status: PermissionStatus
            get() = PermissionStatus.Granted

        override fun launchPermissionRequest() {}
    },
)

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("VisualLintBounds")
@Preview
@Composable
private fun EntryCreationScreenPreview() {
    LogDateTheme {
        EntryCreationScreen(
            useCompactLayout = true,
            onCreateEntry = {},
            onBack = {},
            onNext = {},
            permissionStateHolder = DUMMY_PERMISSION_STATE_HOLDER,
            audioPreviewData = AudioPreviewData.Empty,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("VisualLintBounds")
@Preview
@Composable
private fun EntryCreationScreenTextExpandedPreview() {
    LogDateTheme {
        EntryCreationScreen(
            useCompactLayout = true,
            onCreateEntry = {},
            onBack = {},
            onNext = {},
            permissionStateHolder = DUMMY_PERMISSION_STATE_HOLDER,
            initialState = EntryCompositionState.TEXT_OPEN,
            audioPreviewData = AudioPreviewData.Empty,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("VisualLintBounds")
@Preview
@Composable
private fun EntryCreationScreenVoiceNoteExpandedPreview() {
    LogDateTheme {
        EntryCreationScreen(
            useCompactLayout = true,
            onCreateEntry = {},
            onBack = {},
            onNext = {},
            permissionStateHolder = DUMMY_PERMISSION_STATE_HOLDER,
            audioPreviewData = AudioPreviewData.Empty.copy(
                currentText = "Public speaking sucks so much, you know? I had to give a presentation today on the ethics of AI, and it’s already bad enough this was a group project, but of course one of our team members just didn’t ",
                canUseAudio = true,
            ),
            initialState = EntryCompositionState.AUDIO_OPEN,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("VisualLintBounds")
@Preview
@Composable
private fun EntryCreationScreenCameraExpandedPreview() {
    LogDateTheme {
        EntryCreationScreen(
            useCompactLayout = true,
            onCreateEntry = {},
            onBack = {},
            onNext = {},
            permissionStateHolder = DUMMY_PERMISSION_STATE_HOLDER,
            audioPreviewData = AudioPreviewData.Empty,
            initialState = EntryCompositionState.PHOTO_OPEN,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(device = "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240")
@Composable
private fun EntryCreationScreenPreview_Medium() {
    LogDateTheme {
        EntryCreationScreen(
            useCompactLayout = false,
            onCreateEntry = {},
            onBack = {},
            onNext = {},
            permissionStateHolder = DUMMY_PERMISSION_STATE_HOLDER,
            audioPreviewData = AudioPreviewData.Empty,
        )
    }
}