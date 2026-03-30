package app.logdate.feature.postcards.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.PostcardDocument
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Full-screen viewer for a saved Postcard.
 *
 * Renders the Postcard with ambient parallax and allows two-finger pan/zoom.
 * Photo elements are tappable to navigate to the source moment (intertextuality).
 *
 * @param viewModel The viewer ViewModel providing document state.
 * @param onNavigateBack Callback to navigate back.
 * @param onEditPostcard Callback to open the editor for this Postcard.
 * @param onExportPostcard Callback to open the export sheet.
 * @param onNavigateToMoment Callback when a photo element is tapped, navigating to its source moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostcardViewerScreen(
    viewModel: PostcardViewerViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
    onEditPostcard: (Uuid) -> Unit = {},
    onExportPostcard: (Uuid) -> Unit = {},
    onNavigateToMoment: (Uuid) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? PostcardViewerUiState.Loaded)?.document?.title ?: ""
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val loaded = uiState as? PostcardViewerUiState.Loaded
                    if (loaded != null) {
                        IconButton(onClick = { onEditPostcard(loaded.document.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onExportPostcard(loaded.document.id) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export")
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is PostcardViewerUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is PostcardViewerUiState.Loaded -> {
                    PostcardViewerContent(
                        document = state.document,
                        stickerUriMap = state.stickerUriMap,
                        onPhotoTap = { photo ->
                            onNavigateToMoment(photo.momentRef)
                        },
                    )
                }
                is PostcardViewerUiState.Error -> {
                    Text("Could not load Postcard")
                }
            }
        }
    }
}

@Composable
private fun PostcardViewerContent(
    document: PostcardDocument,
    stickerUriMap: Map<Uuid, String>,
    onPhotoTap: (CanvasElement.Photo) -> Unit,
) {
    val viewportState = rememberCanvasViewportState()

    CanvasViewport(
        state = viewportState,
        modifier = Modifier.fillMaxSize(),
    ) {
        CanvasRenderer(
            document = document,
            stickerUriMap = stickerUriMap,
            onPhotoTap = onPhotoTap,
        )
    }
}
