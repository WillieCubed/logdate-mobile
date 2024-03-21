package app.logdate.feature.journals.ui

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
import androidx.navigation.NavController
import app.logdate.model.Journal
import app.logdate.ui.theme.Spacing

fun NavController.navigateToJournal(journalId: String) {
    navigate("journal/${journalId}")
}

fun NavController.navigateToJournal(journal: Journal) {
    navigate("journal/${journal.id}")
}

@Composable
fun JournalDetailRoute(
    onGoBack: () -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    JournalDetailScreen(state = state, onGoBack = onGoBack, onAddContent = { })
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

