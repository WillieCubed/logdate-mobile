@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_browse_journals
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
fun JournalsOverviewScreen(
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalsOverviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    JournalsOverviewScreenContent(
        journals = state.journals,
        onOpenJournal = onOpenJournal,
        onBrowseJournals = onBrowseJournals,
        onCreateJournal = onCreateJournal,
        onNavigationClick = onNavigationClick,
        modifier = modifier,
    )
}

@Composable
fun JournalsOverviewScreenContent(
    journals: List<JournalListItemUiState>,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            JournalSearchToolbar(
                onNavigationClick = onNavigationClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            JournalListPanel(
                journals = journals,
                onOpenJournal = onOpenJournal,
                onBrowseJournals = onBrowseJournals,
                onCreateJournal = onCreateJournal,
                showLoading = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .widthIn(max = 960.dp),
            )
        }
    }
}

// TODO: Move to :client:ui
@Composable
fun JournalListPanel(
    journals: List<JournalListItemUiState>,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
) {
    Surface(
        modifier = modifier,
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
                        onCreateJournal = onCreateJournal,
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
        JournalsOverviewScreenContent(
            journals = journalsOverviewPreviewData,
            onOpenJournal = {},
            onBrowseJournals = {},
            onCreateJournal = {},
            onNavigationClick = {},
            modifier = Modifier,
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
            onCreateJournal = {},
        )
    }
}

/**
 * A [CornerBasedShape] that represents the shape of a journal item.
 */
internal val JournalShape =
    RoundedCornerShape(
        topEnd = 16.dp,
        bottomEnd = 16.dp,
    )

typealias JournalClickCallback = (journalId: Uuid) -> Unit

private val journalsOverviewPreviewData =
    listOf(
        JournalListItemUiState.ExistingJournal(
            data =
                Journal(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
                    title = "Daily Reflections",
                    description = "A space for everyday thoughts",
                    isFavorited = true,
                    created = Instant.fromEpochMilliseconds(1_740_000_000_000L),
                    lastUpdated = Instant.fromEpochMilliseconds(1_740_000_000_000L),
                ),
        ),
        JournalListItemUiState.ExistingJournal(
            data =
                Journal(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
                    title = "Travel Log",
                    description = "Adventures and explorations",
                    isFavorited = false,
                    created = Instant.fromEpochMilliseconds(1_740_000_500_000L),
                    lastUpdated = Instant.fromEpochMilliseconds(1_740_000_500_000L),
                ),
        ),
        JournalListItemUiState.CreateJournalPlaceholder,
    )
