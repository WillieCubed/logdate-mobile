@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.library.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.feature.library.ui.components.LIBRARY_MEDIA_TRANSITION_KEY
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Stateful media detail screen that loads a note by ID and displays its content with metadata.
 */
@Composable
fun MediaDetailScreen(
    mediaId: Uuid,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onShare: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MediaDetailViewModel =
        koinViewModel(
            parameters = { parametersOf(mediaId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsState()

    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    MediaDetailContent(
        state = uiState,
        isExpanded = isExpanded,
        onBack = onBack,
        onNavigateToJournal = onNavigateToJournal,
        onShare = onShare,
        modifier = modifier,
    )
}

/**
 * Stateless media detail layout.
 *
 * On compact screens, the image fills the screen and metadata is in a bottom sheet.
 * On expanded screens, image and metadata sit side-by-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailContent(
    state: MediaDetailUiState,
    isExpanded: Boolean,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onShare: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        is MediaDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is MediaDetailUiState.Error -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                TextButton(onClick = onBack) {
                    Text("Go back")
                }
            }
        }

        is MediaDetailUiState.ImageContent -> {
            MediaDetailLayout(
                mediaRef = state.mediaRef,
                isVideo = false,
                noteId = state.noteId,
                createdAt = state.createdAt,
                location = state.location,
                journals = state.journals,
                exif = state.exif,
                isExpanded = isExpanded,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { onShare(state.mediaRef) },
                modifier = modifier,
            )
        }

        is MediaDetailUiState.VideoContent -> {
            MediaDetailLayout(
                mediaRef = state.mediaRef,
                isVideo = true,
                noteId = state.noteId,
                createdAt = state.createdAt,
                location = state.location,
                journals = state.journals,
                isExpanded = isExpanded,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { onShare(state.mediaRef) },
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailLayout(
    mediaRef: String,
    isVideo: Boolean,
    noteId: Uuid,
    createdAt: Instant,
    location: NoteLocation?,
    journals: List<JournalReference>,
    exif: ExifDisplayData? = null,
    isExpanded: Boolean,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isExpanded) {
        // Side-by-side: media on left, metadata on right
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(2f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MediaContent(mediaRef = mediaRef, isVideo = isVideo, noteId = noteId)
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
            ) {
                MetadataContent(
                    createdAt = createdAt,
                    location = location,
                    isVideo = isVideo,
                    journals = journals,
                    exif = exif,
                    onNavigateToJournal = onNavigateToJournal,
                )
            }
        }
    } else {
        // Compact: fullscreen media + bottom sheet for metadata
        var showMetadata by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxSize()) {
            MediaContent(
                mediaRef = mediaRef,
                isVideo = isVideo,
                noteId = noteId,
                modifier = Modifier.fillMaxSize(),
            )

            TopAppBar(
                title = {},
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                        )
                    }
                    TextButton(onClick = { showMetadata = true }) {
                        Text("Info")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        }

        if (showMetadata) {
            ModalBottomSheet(onDismissRequest = { showMetadata = false }) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg)
                            .padding(bottom = Spacing.xl),
                ) {
                    MetadataContent(
                        createdAt = createdAt,
                        location = location,
                        isVideo = isVideo,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaContent(
    mediaRef: String,
    isVideo: Boolean,
    noteId: Uuid? = null,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val sharedModifier =
        if (noteId != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = "$LIBRARY_MEDIA_TRANSITION_KEY-$noteId"),
                    animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }

    if (isVideo) {
        VideoPlayerContent(
            uri = mediaRef,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        AsyncImage(
            model = mediaRef,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = sharedModifier.then(modifier).fillMaxSize(),
        )
    }
}

@Composable
private fun MetadataContent(
    createdAt: Instant,
    location: NoteLocation?,
    isVideo: Boolean,
    journals: List<JournalReference> = emptyList(),
    exif: ExifDisplayData? = null,
    onNavigateToJournal: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleLarge,
        )

        // Date
        MetadataRow(
            icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            label = "Captured",
            value = createdAt.toReadableDateTimeShort(),
        )

        // Type
        MetadataRow(
            icon = {
                Icon(
                    if (isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                    contentDescription = null,
                )
            },
            label = "Type",
            value = if (isVideo) "Video" else "Photo",
        )

        // Location
        location?.let { loc ->
            val locationText =
                loc.displayName
                    ?: loc.coordinates?.let { "${it.latitude}, ${it.longitude}" }
                    ?: "Unknown location"
            MetadataRow(
                icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                label = "Location",
                value = locationText,
            )
        }

        // Camera info
        exif?.let { data ->
            val cameraName =
                listOfNotNull(data.cameraMake, data.cameraModel)
                    .joinToString(" ")
                    .ifEmpty { null }
            val settings =
                listOfNotNull(
                    data.aperture?.let { "f/$it" },
                    data.shutterSpeed,
                    data.iso?.let { "ISO $it" },
                    data.focalLength?.let { "${it}mm" },
                ).joinToString("  ·  ")

            if (cameraName != null || settings.isNotEmpty()) {
                MetadataRow(
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                    label = "Camera",
                    value = listOfNotNull(cameraName, settings.ifEmpty { null }).joinToString("\n"),
                )
            }
        }

        // Appears in journals
        if (journals.isNotEmpty()) {
            AppearsInSection(
                journals = journals,
                onNavigateToJournal = onNavigateToJournal,
            )
        }
    }
}

@Composable
private fun AppearsInSection(
    journals: List<JournalReference>,
    onNavigateToJournal: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Appears in",
            style = MaterialTheme.typography.titleMedium,
        )
        journals.forEach { journal ->
            TextButton(onClick = { onNavigateToJournal(journal.id) }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Book,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = journal.title)
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
