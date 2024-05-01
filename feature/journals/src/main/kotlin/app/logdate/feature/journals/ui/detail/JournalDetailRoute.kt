package app.logdate.feature.journals.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.ui.theme.Spacing

@Composable
fun JournalDetailRoute(
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    JournalDetailScreen(
        state = state,
        onGoBack = onGoBack,
        onAddContent = { },
        onDeleteJournal = {
            viewModel.deleteJournal(onJournalDeleted)
        },
    )
}

@Composable
fun JournalDetailPlaceholder() {
    Row(
        modifier = Modifier
            .padding(Spacing.lg)
            .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading..."
        )
    }
}

