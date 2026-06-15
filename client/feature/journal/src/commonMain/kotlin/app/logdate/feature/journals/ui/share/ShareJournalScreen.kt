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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalShape
import app.logdate.feature.journals.ui.deriveCoverColor
import app.logdate.shared.model.Journal
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyStandardContentWidth
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.ui.generated.resources.common_go_back
import logdate.client.ui.generated.resources.common_loading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Screen for sharing a journal with others.
 *
 * Provides options to share via the system share sheet, a QR code, or Instagram.
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
        onShareQrCode = { journal -> viewModel.shareJournalQrCode(journal) },
        onShareJournal = { journal -> viewModel.shareJournal(journal) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareJournalScreenContent(
    uiState: ShareJournalUiState,
    onGoBack: () -> Unit,
    onShareToInstagram: (Journal) -> Unit,
    onShareQrCode: (Journal) -> Unit,
    onShareJournal: (Journal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.journal_share_label)) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(UiRes.string.common_go_back),
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
                    Text(stringResource(UiRes.string.common_loading))
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
                    onShareQrCode = { onShareQrCode(state.journal) },
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
 * @param onShareQrCode Callback when sharing a QR code
 * @param onShareJournal Callback when using general share sheet
 * @param modifier Modifier for this composable
 */
@Composable
fun ShareJournalContent(
    journal: Journal,
    onShareToInstagram: () -> Unit,
    onShareQrCode: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            ShareJournalPrimaryPane(
                journal = journal,
                modifier = Modifier.fillMaxSize(),
            )
        },
        endPane = {
            ShareJournalActionPane(
                onShareToInstagram = onShareToInstagram,
                onShareQrCode = onShareQrCode,
                onShareJournal = onShareJournal,
                modifier = Modifier.fillMaxSize(),
            )
        },
        standardContent = {
            ShareJournalStandardContent(
                journal = journal,
                onShareToInstagram = onShareToInstagram,
                onShareQrCode = onShareQrCode,
                onShareJournal = onShareJournal,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun ShareJournalStandardContent(
    journal: Journal,
    onShareToInstagram: () -> Unit,
    onShareQrCode: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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

        ShareJournalDescription()

        ShareJournalActions(
            onShareQrCode = onShareQrCode,
            onShareJournal = onShareJournal,
            onShareToInstagram = onShareToInstagram,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        NearbySharingInfo()
    }
}

@Composable
private fun ShareJournalPrimaryPane(
    journal: Journal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ShareJournalCard(
            journal = journal,
            modifier = Modifier.widthIn(max = 360.dp),
        )

        ShareJournalDescription()
    }
}

@Composable
private fun ShareJournalActionPane(
    onShareToInstagram: () -> Unit,
    onShareQrCode: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ShareJournalActions(
            onShareQrCode = onShareQrCode,
            onShareJournal = onShareJournal,
            onShareToInstagram = onShareToInstagram,
        )

        NearbySharingInfo()
    }
}

@Composable
private fun ShareJournalDescription() {
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
                    text = stringResource(Res.string.share_journal_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareJournalActions(
    onShareQrCode: () -> Unit,
    onShareJournal: () -> Unit,
    onShareToInstagram: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = onShareQrCode,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("share_journal_qr_action"),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = stringResource(Res.string.share_qr_code),
                    modifier = Modifier.size(24.dp),
                )
            }

            Button(
                onClick = onShareJournal,
                modifier =
                    Modifier
                        .weight(3f)
                        .height(56.dp)
                        .testTag("share_journal_sheet_action"),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(text = stringResource(Res.string.share))
            }
        }

        Button(
            onClick = onShareToInstagram,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Text(text = stringResource(Res.string.share_to_instagram))
        }
    }
}

@Composable
private fun NearbySharingInfo() {
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
            text = stringResource(Res.string.journal_invite_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
