@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.journals.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalShape
import app.logdate.feature.journals.ui.deriveCoverColor
import app.logdate.shared.model.Journal
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyStandardContentWidth
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.journal.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Screen for sharing a journal with others.
 *
 * Provides options to share via the system share sheet or Instagram.
 *
 * @param journalId ID of the journal to share
 * @param onGoBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareJournalScreen(
    journalId: String,
    onGoBack: () -> Unit,
) {
    val viewModel = koinInject<ShareJournalViewModel>()
    val parsedId =
        remember(journalId) {
            kotlin.uuid.Uuid.parse(journalId)
        }

    LaunchedEffect(parsedId) {
        viewModel.setJournalId(parsedId)
    }

    val uiState by viewModel.uiState.collectAsState()

    ShareJournalScreenContent(
        uiState = uiState,
        onGoBack = onGoBack,
        onShareToInstagram = { journal -> viewModel.shareToInstagram(journal) },
        onShareJournal = { journal -> viewModel.shareJournal(journal) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareJournalScreenContent(
    uiState: ShareJournalUiState,
    onGoBack: () -> Unit,
    onShareToInstagram: (Journal) -> Unit,
    onShareJournal: (Journal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.share_journal_2)) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.go_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is ShareJournalUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.loading))
                }
            }
            is ShareJournalUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.failed_to_load_journal))
                }
            }
            is ShareJournalUiState.Success -> {
                ShareJournalContent(
                    journal = state.journal,
                    onShareToInstagram = { onShareToInstagram(state.journal) },
                    onShareJournal = { onShareJournal(state.journal) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

/**
 * Content of the share journal screen.
 *
 * Displays a share-specific journal card alongside sharing actions.
 *
 * @param journal Journal to be shared
 * @param onShareToInstagram Callback when sharing to Instagram
 * @param onShareJournal Callback when using general share sheet
 * @param modifier Modifier for this composable
 */
@Composable
fun ShareJournalContent(
    journal: Journal,
    onShareToInstagram: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg)
                .applyStandardContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ShareJournalCard(
            journal = journal,
            modifier = Modifier.widthIn(max = 240.dp),
        )

        // Web availability notice
        MaterialContainer {
            SurfaceItem {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.this_journal_is_available_on_the_web_anyone_with_the_link_can_view_it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Share buttons — original layout preserved
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = onShareToInstagram,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }

            Button(
                onClick = onShareJournal,
                modifier =
                    Modifier
                        .weight(3f)
                        .height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(text = stringResource(Res.string.share))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom nearby sharing info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.also_sharing_to_nearby_logdate_contacts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.bring_your_devices_together_to_invite_someone_to_add_stuff),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A share-specific journal card that renders the journal cover shape
 * with an info strip below showing the title and last updated date.
 */
@Composable
private fun ShareJournalCard(
    journal: Journal,
    modifier: Modifier = Modifier,
) {
    val coverColor = remember(journal.id) { deriveCoverColor(journal.id) }
    val coverTextColor =
        remember(coverColor) {
            if (coverColor.luminance() > 0.5f) {
                Color.Black.copy(alpha = 0.87f)
            } else {
                Color.White.copy(alpha = 0.95f)
            }
        }

    Surface(
        modifier =
            modifier.shadow(elevation = 4.dp, shape = JournalShape),
        shape = JournalShape,
    ) {
        Column {
            // Cover area with title overlay
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(AspectRatios.JOURNAL_COVER)
                        .background(coverColor),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = journal.title,
                    modifier = Modifier.padding(Spacing.lg),
                    style = MaterialTheme.typography.titleMedium,
                    color = coverTextColor,
                )
            }

            // Info strip below the cover
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = journal.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.last_updated_prefix, journal.lastUpdated.toReadableDateShort()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Data class for journal sharing information.
 *
 * @param journalId String ID of the journal to share
 */
data class JournalShareData(
    val journalId: String,
)
