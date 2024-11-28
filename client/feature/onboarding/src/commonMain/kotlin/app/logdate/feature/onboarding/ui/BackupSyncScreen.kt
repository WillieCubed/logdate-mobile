package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.section_title_backup_sync
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupSyncScreen(
    onBack: () -> Unit,
    useCompactLayout: Boolean = false,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    BackupSyncScreenContent(
        useCompactLayout = useCompactLayout,
        onBack = onBack,
        onPlanSelected = viewModel::selectPlan,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSyncScreenContent(
    useCompactLayout: Boolean,
    onBack: () -> Unit,
    onPlanSelected: (option: LogDateBackupPlanOption) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        supplementalContent = {
            Scaffold(
                topBar = {
                    LargeTopAppBar(
                        title = { Text(stringResource(Res.string.section_title_backup_sync)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors().copy(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) { contentPadding ->
                Column(
                    Modifier
                        .padding(contentPadding)
                        .padding(Spacing.lg),
                ) {
                    Text("All your memories are stored securely on your device by default. Sign up for a LogDate Account so you never lose your memories.")
                }
            }
        },
        mainContent = {
            if (useCompactLayout) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(Res.string.section_title_backup_sync)) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Default.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )

                    },
                ) { contentPadding ->
                    MainContent(
                        onPlanSelected = onPlanSelected,
                        modifier = Modifier
                            .padding(contentPadding)
                            .nestedScroll(
                                scrollBehavior.nestedScrollConnection
                            ),
                    )
                }
            } else {
                // Just show content; app bar is in the supplemental content
                MainContent(
                    onPlanSelected = {},
                )
            }
        },
    )
}

@Composable
private fun MainContent(
    onPlanSelected: (option: LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // TODO: Use data classes for plan options
        item {
            PlanContentCard(
                option = LogDateBackupPlanOption.BASIC,
                title = "Basic",
                description = "Max 10 GB of text, photos, videos, and voice notes. Photos and videos will be compressed in high quality (1080p).",
                price = "Free",
                onPlanSelected = onPlanSelected,
            )
        }
        item {
            PlanContentCard(
                option = LogDateBackupPlanOption.STANDARD,
                title = "Premium Plan",
                description = "Includes up to 2 TB of storage for text, photo, video, and voice notes.\n" +
                        "Photos and videos will be stored in original quality.",
                price = "$12/month",
                onPlanSelected = onPlanSelected,
            )
        }
    }
}

@Composable
internal fun PlanContentCard(
    option: LogDateBackupPlanOption,
    title: String,
    description: String,
    price: String,
    onPlanSelected: (option: LogDateBackupPlanOption) -> Unit,
) {
    Card(
        onClick = { onPlanSelected(option) },
        modifier = Modifier.padding(Spacing.lg),
    ) {
        Column {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title)
                Text(description)
            }
            Text(price)
        }
    }
}

@Preview
@Composable
private fun BackupSyncScreenPreview_Compact() {
    BackupSyncScreenContent(
        useCompactLayout = true,
        onBack = {},
        onPlanSelected = {},
    )
}

@Preview
@Composable
private fun BackupSyncScreenPreview_Medium() {
    BackupSyncScreenContent(
        useCompactLayout = false,
        onBack = {},
        onPlanSelected = {},
    )
}