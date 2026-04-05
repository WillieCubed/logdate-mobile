package app.logdate.feature.postcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.client.database.entities.PostcardEntity
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.ui.common.ContextMenuArea
import app.logdate.ui.common.ContextMenuItem
import app.logdate.ui.common.focusableWithRing
import app.logdate.ui.common.verticalScrollbar
import coil3.compose.AsyncImage
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Grid collection view for the user's Postcards.
 *
 * Lives in Library > Postcards tab. Shows all saved Postcards as cards
 * with a thumbnail of the first photo and the title. Tap opens the viewer;
 * FAB creates a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostcardsCollectionScreen(
    viewModel: PostcardsCollectionViewModel = koinViewModel(),
    onOpenPostcard: (Uuid) -> Unit = {},
    onEditPostcard: (Uuid) -> Unit = {},
    onDeletePostcard: (Uuid) -> Unit = {},
    onCreateNew: () -> Unit = {},
) {
    val postcards by viewModel.postcards.collectAsState()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columnCount =
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> 4
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> 3
            else -> 2
        }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Postcards") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) {
                Icon(Icons.Filled.Add, contentDescription = "New Postcard")
            }
        },
    ) { paddingValues ->
        if (postcards.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No Postcards yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .padding(paddingValues)
                        .verticalScrollbar(gridState),
            ) {
                items(postcards, key = { it.id.toString() }) { postcard ->
                    ContextMenuArea(
                        items =
                            listOf(
                                ContextMenuItem("Open") { onOpenPostcard(postcard.id) },
                                ContextMenuItem("Edit") { onEditPostcard(postcard.id) },
                                ContextMenuItem("Delete") { onDeletePostcard(postcard.id) },
                            ),
                    ) {
                        PostcardCard(
                            postcard = postcard,
                            onClick = { onOpenPostcard(postcard.id) },
                        )
                    }
                }
            }
        }
    }
}

private val thumbnailJson = PostcardDocument.json

@Composable
private fun PostcardCard(
    postcard: PostcardEntity,
    onClick: () -> Unit,
) {
    val thumbnailUri =
        remember(postcard.documentJson) {
            try {
                val doc =
                    thumbnailJson.decodeFromString(
                        PostcardDocument.serializer(),
                        postcard.documentJson,
                    )
                doc.elements
                    .filterIsInstance<CanvasElement.Photo>()
                    .firstOrNull()
                    ?.mediaUri
            } catch (e: Exception) {
                Napier.w("Failed to extract thumbnail from postcard", e)
                null
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .focusableWithRing()
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = postcard.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (thumbnailUri != null) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
            )
        }
    }
}
