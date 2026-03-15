@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui.detail

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
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
                createdAt = state.createdAt,
                location = state.location,
                isExpanded = isExpanded,
                onBack = onBack,
                modifier = modifier,
            )
        }

        is MediaDetailUiState.VideoContent -> {
            MediaDetailLayout(
                mediaRef = state.mediaRef,
                isVideo = true,
                createdAt = state.createdAt,
                location = state.location,
                isExpanded = isExpanded,
                onBack = onBack,
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
    createdAt: Instant,
    location: NoteLocation?,
    isExpanded: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isExpanded) {
        // Side-by-side: media on left, metadata on right
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(2f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MediaContent(mediaRef = mediaRef, isVideo = isVideo)
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
    modifier: Modifier = Modifier,
) {
    if (isVideo) {
        // Video playback placeholder — will be replaced in task #6
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = mediaRef,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MetadataContent(
    createdAt: Instant,
    location: NoteLocation?,
    isVideo: Boolean,
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
