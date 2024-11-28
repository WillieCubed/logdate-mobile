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
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_browse_journals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel


@Composable
internal fun JournalsOverviewScreen(
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    viewModel: JournalsOverviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // TODO: Add UI for larger screens
    JournalListPanel(
        journals = state.journals,
        onOpenJournal = onOpenJournal,
        onBrowseJournals = onBrowseJournals,
        showLoading = false,
    )
}

// TODO: Move to :client:ui
@Composable
fun JournalListPanel(
    journals: List<JournalListItemUiState>,
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
                        Text(text = stringResource(Res.string.action_browse_journals))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun JournalsScreenPreview() {
    LogDateTheme {
        JournalsOverviewScreen(
            onOpenJournal = {},
            onBrowseJournals = {},
        )
    }
}

@Preview
@Composable
private fun JournalsScreenPreview_Empty() {
    LogDateTheme {
        JournalListPanel(
            journals = emptyList(),
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
