package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.model.LibraryItem
import app.logdate.ui.GenericLoadingScreen

@Composable
fun LibraryRoute(
    onGoToItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ListScreen(state, onGoToItem, modifier)
}

@Composable
internal fun ListScreen(
    state: LibraryUiState,
    onGoToItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        LibraryUiState.Loading -> GenericLoadingScreen(modifier)
        is LibraryUiState.Success -> Content(state.data, onGoToItem, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Content(
    items: List<LibraryItem>,
    onGoToItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items = items) { index, item ->
            ListItem(
                headlineContent = {},
//                trailingContent = { Text(text = item.date) },
                leadingContent = {
//                    Checkbox(
//                        checked = item.isBookmarked,
//                        onCheckedChange = { isChecked ->
//                            onBookmarkItem(item.id, isChecked)
//                        }
//                    )
                },
                modifier = Modifier
//                    .clickable {
//                        onGoToItem(item.id)
//                    }
                    .testTag("item_$index"),
            )
        }
    }
}
