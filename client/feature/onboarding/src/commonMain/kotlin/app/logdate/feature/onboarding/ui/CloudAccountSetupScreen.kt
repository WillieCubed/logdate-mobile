@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import app.logdate.feature.core.account.OnboardingStep as CloudAccountOnboardingStep
import logdate.client.ui.generated.resources.Res as UiRes

const val CLOUD_ACCOUNT_SETUP_ROOT_TAG = "onboarding_account_root"
const val CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG = "onboarding_account_primary_action"
const val CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG = "onboarding_account_skip_action"
const val CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG = "onboarding_account_skip_option"

@Composable
fun CloudAccountSetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    useCompactLayout: Boolean? = null,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = koinViewModel(),
) {
    BoxWithConstraints(modifier = modifier) {
        val resolvedUseCompactLayout = useCompactLayout ?: (maxWidth < 700.dp)
        var showAccountFlow by remember { mutableStateOf(false) }
        val cloudAccountViewModel = koinViewModel<CloudAccountOnboardingViewModel>()

        LaunchedEffect(showAccountFlow) {
            if (showAccountFlow) {
                cloudAccountViewModel.resetFlow()
                cloudAccountViewModel.setInitialStep(CloudAccountOnboardingStep.DisplayName)
            }
        }

        if (showAccountFlow) {
            CloudAccountOnboardingScreen(
                viewModel = cloudAccountViewModel,
                onAccountCreated = onContinue,
                onSkipOnboarding = onSkip,
                onBack = {
                    cloudAccountViewModel.resetFlow()
                    showAccountFlow = false
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        CloudAccountSetupContent(
            useCompactLayout = resolvedUseCompactLayout,
            onBack = onBack,
            onContinue = { showAccountFlow = true },
            onSkip = onSkip,
            onPlanSelected = onboardingViewModel::selectPlan,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun CloudAccountSetupContent(
    useCompactLayout: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier,
    selectedOption: LogDateBackupPlanOption? = null,
    onOptionSelected: (LogDateBackupPlanOption) -> Unit = {},
) {
    if (useCompactLayout) {
        BackupSyncCompactContent(
            onBack = onBack,
            onContinue = onContinue,
            onSkip = onSkip,
            onPlanSelected = onPlanSelected,
            modifier = modifier,
        )
    } else {
        AdaptiveLayout(
            useCompactLayout = false,
            modifier = modifier.testTag(CLOUD_ACCOUNT_SETUP_ROOT_TAG),
            supplementalContent = {
                // Left pane: title + description
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UiRes.string.common_back),
                        )
                    }
                    Text(
                        stringResource(Res.string.backup_and_sync),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(Res.string.onboarding_cloud_backup_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            mainContent = {
                // Right pane: plan cards + buttons
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            ActionButtons(onContinue = onContinue, onSkip = onSkip)
                        }
                    },
                ) { contentPadding ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(contentPadding)
                                .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        PlanCards(onPlanSelected = onPlanSelected)
                    }
                }
            },
        )
    }
}

@Composable
private fun BackupSyncCompactContent(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                ActionButtons(
                    onContinue = onContinue,
                    onSkip = onSkip,
                    modifier = Modifier.widthIn(max = 444.dp),
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .testTag(CLOUD_ACCOUNT_SETUP_ROOT_TAG)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UiRes.string.common_back),
                        )
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 444.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        stringResource(Res.string.backup_and_sync),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    Text(
                        stringResource(Res.string.onboarding_cloud_backup_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PlanCards(onPlanSelected = onPlanSelected)
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG),
        ) {
            Text(stringResource(UiRes.string.common_continue))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag(CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG).testTag(CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG),
        ) {
            Text(stringResource(Res.string.continue_without_cloud_sync))
        }
        Text(
            text = stringResource(Res.string.onboarding_sync_later_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanCards(onPlanSelected: (LogDateBackupPlanOption) -> Unit) {
    PlanCard(
        title = stringResource(Res.string.onboarding_plan_basic_title),
        description = stringResource(Res.string.onboarding_plan_basic_description),
        price = stringResource(Res.string.onboarding_plan_basic_price),
        onClick = { onPlanSelected(LogDateBackupPlanOption.BASIC) },
    )
    PlanCard(
        title = stringResource(Res.string.onboarding_plan_standard_title),
        description = stringResource(Res.string.onboarding_plan_standard_description),
        price = stringResource(Res.string.onboarding_plan_standard_price),
        onClick = { onPlanSelected(LogDateBackupPlanOption.STANDARD) },
    )
}

@Composable
private fun PlanCard(
    title: String,
    description: String,
    price: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview
@Composable
private fun CloudAccountSetupScreenPreview() {
    LogDateTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            onBack = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun CloudAccountSetupScreenPreview_Split() {
    LogDateTheme {
        CloudAccountSetupContent(
            useCompactLayout = false,
            onBack = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
        )
    }
}
