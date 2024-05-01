package app.logdate.feature.rewind.ui.detail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.rewind.ui.RewindDetailViewModel

@Composable
fun RewindDetailRoute(
    onGoBack: () -> Unit,
    viewModel: RewindDetailViewModel = hiltViewModel(),
) {
    RewindDetailScreen(onGoBack = onGoBack)
}

/**
 * The main screen to view a journal's contents.
 */
@Composable
fun RewindDetailScreen(
    onGoBack: () -> Unit,
) {
    Text("Rewind Detail Screen")
}
