package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.data.journals.TEST_JOURNALS
import app.logdate.feature.journals.R
import app.logdate.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing

@Composable
fun JournalsRoute(
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    JournalsScreen(state, onOpenJournal, onBrowseJournals, modifier)
}

@Composable
internal fun JournalsScreen(
    state: JournalsUiState,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JournalListPanel(
        journals = (state as? JournalsUiState.Success)?.journals.orEmpty(),
        onOpenJournal = onOpenJournal,
        onBrowseJournals = onBrowseJournals,
        modifier = modifier,
        showLoading = state is JournalsUiState.Loading,
    )
}

@Composable
fun JournalListPanel(
    journals: List<Journal>,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        JournalListPlaceholder(isVisible = showLoading)
        AnimatedVisibility(
            visible = showLoading.not(),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            if (journals.isEmpty()) {
                NoJournalsScreen()
            } else {
//                JournalList(state.journals, onOpenJournal, modifier)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    JournalCoverFlowCarousel(
                        journals = journals,
                        onOpenJournal = onOpenJournal,
                        modifier = modifier.fillMaxHeight(),
                    )
                    TextButton(onClick = onBrowseJournals) {
                        Text(text = stringResource(R.string.action_browse_journals))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalsScreenPreview() {
    LogDateTheme {
        JournalsScreen(
            state = JournalsUiState.Success(TEST_JOURNALS),
            onOpenJournal = {},
            onBrowseJournals = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalsScreenPreview_Empty() {
    LogDateTheme {
        JournalsScreen(
            state = JournalsUiState.Success(listOf()),
            onOpenJournal = {},
            onBrowseJournals = {},
        )
    }
}

/**
 * A [CornerBasedShape] that represents the shape of a journal item.
 */
internal val JournalShape = RoundedCornerShape(
    topEnd = 16.dp,
    bottomEnd = 16.dp,
)

typealias JournalClickCallback = (journalId: String) -> Unit
