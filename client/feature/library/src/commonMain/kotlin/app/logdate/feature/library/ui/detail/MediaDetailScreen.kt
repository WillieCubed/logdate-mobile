@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.feature.editor.ui.video.VideoPlayerContent
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val presenterState by viewModel.presenterState.collectAsStateWithLifecycle()

    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    MediaDetailContent(
        state = uiState,
        presenterState = presenterState,
        isExpanded = isExpanded,
        onBack = {
            viewModel.stopPresenting()
            onBack()
        },
        onNavigateToJournal = onNavigateToJournal,
        onShare = onShare,
        onStartPresenting = viewModel::startPresenting,
        onStopPresenting = viewModel::stopPresenting,
        onPresentItem = viewModel::presentItem,
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
    presenterState: PresenterState = PresenterState(),
    isExpanded: Boolean,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onShare: (String) -> Unit = {},
    onStartPresenting: () -> Unit = {},
    onStopPresenting: () -> Unit = {},
    onPresentItem: (Int) -> Unit = {},
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
                locationDisplayName = state.locationDisplayName,
                journals = state.journals,
                exif = state.exif,
                isExpanded = isExpanded,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { onShare(state.mediaRef) },
                presenterState = presenterState,
                onStartPresenting = onStartPresenting,
                onStopPresenting = onStopPresenting,
                onPresentItem = onPresentItem,
                modifier = modifier,
            )
        }

        is MediaDetailUiState.VideoContent -> {
            MediaDetailLayout(
                mediaRef = state.mediaRef,
                isVideo = true,
                createdAt = state.createdAt,
                locationDisplayName = state.locationDisplayName,
                journals = state.journals,
                isExpanded = isExpanded,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { onShare(state.mediaRef) },
                presenterState = presenterState,
                onStartPresenting = onStartPresenting,
                onStopPresenting = onStopPresenting,
                onPresentItem = onPresentItem,
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
    locationDisplayName: String? = null,
    journals: List<JournalReference>,
    exif: ExifDisplayData? = null,
    isExpanded: Boolean,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onShare: () -> Unit = {},
    presenterState: PresenterState = PresenterState(),
    onStartPresenting: () -> Unit = {},
    onStopPresenting: () -> Unit = {},
    onPresentItem: (Int) -> Unit = {},
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
                    locationDisplayName = locationDisplayName,
                    isVideo = isVideo,
                    journals = journals,
                    exif = exif,
                    onNavigateToJournal = onNavigateToJournal,
                )
            }
        }
    } else {
        // Compact: fullscreen media + swipeable bottom sheet for metadata
        val scaffoldState =
            rememberBottomSheetScaffoldState(
                bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded),
            )

        BottomSheetScaffold(
            modifier = modifier,
            scaffoldState = scaffoldState,
            sheetPeekHeight = 72.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            containerColor = Color.Black,
            sheetContent = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg)
                            .padding(bottom = Spacing.xl),
                ) {
                    MetadataContent(
                        createdAt = createdAt,
                        locationDisplayName = locationDisplayName,
                        isVideo = isVideo,
                        journals = journals,
                        exif = exif,
                        onNavigateToJournal = onNavigateToJournal,
                    )
                }
            },
            topBar = {
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
                        if (presenterState.isExternalDisplayAvailable) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (presenterState.isPresenting) {
                                        onStopPresenting()
                                    } else {
                                        onStartPresenting()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        if (presenterState.isPresenting) {
                                            Icons.Filled.StopScreenShare
                                        } else {
                                            Icons.Filled.ScreenShare
                                        },
                                    contentDescription =
                                        if (presenterState.isPresenting) "Stop presenting" else "Present",
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("media_detail_share_action"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
        ) { innerPadding ->
            val focusRequester = FocusRequester()
            if (presenterState.isPresenting) {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .then(
                            if (presenterState.isPresenting) {
                                Modifier
                                    .focusRequester(focusRequester)
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        when (event.key) {
                                            Key.DirectionRight -> {
                                                val next =
                                                    (presenterState.currentIndex + 1)
                                                        .coerceAtMost(presenterState.mediaItems.size - 1)
                                                onPresentItem(next)
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                val prev =
                                                    (presenterState.currentIndex - 1)
                                                        .coerceAtLeast(0)
                                                onPresentItem(prev)
                                                true
                                            }
                                            Key.Escape -> {
                                                onStopPresenting()
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                // Presenter navigation strip
                if (presenterState.isPresenting && presenterState.mediaItems.size > 1) {
                    PresenterNavigationStrip(
                        items = presenterState.mediaItems,
                        currentIndex = presenterState.currentIndex,
                        onSelectItem = onPresentItem,
                    )
                }

                if (presenterState.isPresenting && presenterState.mediaItems.size > 1) {
                    val pagerState =
                        rememberPagerState(
                            initialPage = presenterState.currentIndex,
                            pageCount = { presenterState.mediaItems.size },
                        )

                    // Sync pager swipes to the presentation
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            onPresentItem(page)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) { page ->
                        val item = presenterState.mediaItems[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MediaContent(
                                mediaRef = item.uri,
                                isVideo = item.isVideo,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaContent(
                            mediaRef = mediaRef,
                            isVideo = isVideo,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Stop presenting button
                if (presenterState.isPresenting) {
                    FilledTonalButton(
                        onClick = onStopPresenting,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Icon(Icons.Filled.StopScreenShare, contentDescription = null)
                        Spacer(
                            modifier = Modifier.size(Spacing.sm),
                        )
                        Text("Stop Presenting")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresenterNavigationStrip(
    items: List<PresenterMediaItem>,
    currentIndex: Int,
    onSelectItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Presenting \u2022 ${currentIndex + 1} of ${items.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            val thumbnailShape = RoundedCornerShape(6.dp)
            items.forEachIndexed { index, item ->
                item(key = item.uid) {
                    val isSelected = index == currentIndex
                    Box(
                        modifier =
                            Modifier
                                .size(if (isSelected) 56.dp else 48.dp)
                                .clip(thumbnailShape)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            thumbnailShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).clickable { onSelectItem(index) },
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
        VideoPlayerContent(
            uri = mediaRef,
            modifier = modifier.fillMaxSize(),
        )
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
    locationDisplayName: String? = null,
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
        locationDisplayName?.let { name ->
            MetadataRow(
                icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                label = "Location",
                value = name,
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
