package app.logdate.feature.journals.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.data.journals.TEST_JOURNALS
import app.logdate.ui.theme.LogDateTheme

@Composable
fun JournalsRoute(
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier,
    viewModel: JournalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    JournalsScreen(state, onOpenJournal, modifier)
}

@Composable
internal fun JournalsScreen(
    state: JournalsUiState,
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier
) {
    when (state) {
        JournalsUiState.Loading -> JournalListPlaceholder()
        is JournalsUiState.Success -> {
            if (state.journals.isEmpty()) {
                NoJournalsScreen()
            } else {
                JournalList(state.journals, onOpenJournal, modifier)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalsScreenPreview() {
    LogDateTheme {
        JournalsScreen(state = JournalsUiState.Success(TEST_JOURNALS), onOpenJournal = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalsScreenPreview_Empty() {
    LogDateTheme {
        JournalsScreen(state = JournalsUiState.Success(listOf()), onOpenJournal = {})
    }
}

/**
 * A [CornerBasedShape] that represents the shape of a journal item.
 */
internal val JournalShape = RoundedCornerShape(
    topEnd = 16.dp,
    bottomEnd = 16.dp,
)

typealias JournalOpenCallback = (journalId: String) -> Unit
