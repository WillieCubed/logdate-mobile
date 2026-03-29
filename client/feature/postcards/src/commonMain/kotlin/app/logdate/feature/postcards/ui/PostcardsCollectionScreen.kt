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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.client.database.entities.PostcardEntity
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Grid collection view for the user's Postcards.
 *
 * Lives in Library → Postcards tab. Shows all saved Postcards as cards
 * with title and date. Tap opens the viewer; FAB creates a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostcardsCollectionScreen(
    viewModel: PostcardsCollectionViewModel = koinViewModel(),
    onOpenPostcard: (Uuid) -> Unit = {},
    onCreateNew: () -> Unit = {},
) {
    val postcards by viewModel.postcards.collectAsState()

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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(paddingValues),
            ) {
                items(postcards, key = { it.id.toString() }) { postcard ->
                    PostcardCard(
                        postcard = postcard,
                        onClick = { onOpenPostcard(postcard.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostcardCard(
    postcard: PostcardEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = postcard.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
