@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import app.logdate.feature.core.account.OnboardingStep as CloudAccountOnboardingStep
import logdate.client.ui.generated.resources.Res as UiRes

const val CLOUD_ACCOUNT_SETUP_ROOT_TAG = "onboarding_account_root"
const val CLOUD_ACCOUNT_SETUP_CREATE_OPTION_TAG = "onboarding_account_create_option"
const val CLOUD_ACCOUNT_SETUP_SIGN_IN_OPTION_TAG = "onboarding_account_sign_in_option"
const val CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG = "onboarding_account_skip_option"
const val CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG = "onboarding_account_primary_action"
const val CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG = "onboarding_account_skip_action"

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
        var selectedOption by remember { mutableStateOf<CloudSetupOption?>(null) }
        var activeFlow by remember { mutableStateOf<CloudSetupFlow?>(null) }
        val cloudAccountViewModel = koinViewModel<CloudAccountOnboardingViewModel>()

        LaunchedEffect(activeFlow) {
            when (activeFlow) {
                CloudSetupFlow.CREATE_ACCOUNT -> {
                    cloudAccountViewModel.resetFlow()
                    cloudAccountViewModel.setInitialStep(CloudAccountOnboardingStep.DisplayName)
                }
                CloudSetupFlow.SIGN_IN -> {
                    cloudAccountViewModel.resetFlow()
                    cloudAccountViewModel.setInitialStep(CloudAccountOnboardingStep.SignIn)
                }
                null -> Unit
            }
        }

        if (activeFlow != null) {
            CloudAccountOnboardingScreen(
                viewModel = cloudAccountViewModel,
                onAccountCreated = onContinue,
                onSkipOnboarding = onSkip,
                onBack = {
                    cloudAccountViewModel.resetFlow()
                    activeFlow = null
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        CloudAccountSetupContent(
            useCompactLayout = resolvedUseCompactLayout,
            selectedOption = selectedOption,
            onBack = onBack,
            onOptionSelected = { selectedOption = it },
            onContinue = {
                when (selectedOption) {
                    CloudSetupOption.CREATE_ACCOUNT -> activeFlow = CloudSetupFlow.CREATE_ACCOUNT
                    CloudSetupOption.SIGN_IN -> activeFlow = CloudSetupFlow.SIGN_IN
                    CloudSetupOption.SKIP -> onSkip()
                    null,
                    -> Unit
                }
            },
            onSkip = onSkip,
            onPlanSelected = onboardingViewModel::selectPlan,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountSetupContent(
    useCompactLayout: Boolean,
    selectedOption: CloudSetupOption?,
    onBack: () -> Unit,
    onOptionSelected: (CloudSetupOption) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        modifier = modifier,
        supplementalContent = {
            Scaffold(
                topBar = {
                    LargeTopAppBar(
                        title = { Text(stringResource(Res.string.backup_and_sync)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = stringResource(UiRes.string.common_back),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors().copy(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) { contentPadding ->
                Column(
                    modifier =
                        Modifier
                            .padding(contentPadding)
                            .padding(Spacing.lg)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    InfoSection()
                }
            }
        },
        mainContent = {
            if (useCompactLayout) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(Res.string.backup_and_sync)) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Default.ArrowBack,
                                        contentDescription = stringResource(UiRes.string.common_back),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { contentPadding ->
                    MainContent(
                        selectedOption = selectedOption,
                        onOptionSelected = onOptionSelected,
                        onContinue = onContinue,
                        onSkip = onSkip,
                        onPlanSelected = onPlanSelected,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            } else {
                MainContent(
                    selectedOption = selectedOption,
                    onOptionSelected = onOptionSelected,
                    onContinue = onContinue,
                    onSkip = onSkip,
                    onPlanSelected = onPlanSelected,
                )
            }
        },
    )
}

@Composable
private fun InfoSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Card {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.logdate_cloud),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text =
                        stringResource(
                            Res.string.onboarding_cloud_sync_description,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.secure_with_passkeys),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text =
                        stringResource(
                            Res.string.account_passkey_biometric_description,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    selectedOption: CloudSetupOption?,
    onOptionSelected: (CloudSetupOption) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(Spacing.lg).testTag(CLOUD_ACCOUNT_SETUP_ROOT_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            InfoSection()
        }

        item {
            Text(
                text = stringResource(Res.string.choose_how_youd_like_to_proceed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        // Account setup options
        item {
            CloudSetupOptionCard(
                icon = Icons.Default.PersonAdd,
                title = "Create LogDate Cloud Account",
                description = "Set up a new account with secure passkey authentication and choose your storage plan.",
                isSelected = selectedOption == CloudSetupOption.CREATE_ACCOUNT,
                onClick = { onOptionSelected(CloudSetupOption.CREATE_ACCOUNT) },
                modifier = Modifier.testTag(CLOUD_ACCOUNT_SETUP_CREATE_OPTION_TAG),
            )
        }

        item {
            CloudSetupOptionCard(
                icon = Icons.Default.AccountCircle,
                title = "Sign In to Existing Account",
                description = "Already have a LogDate Cloud account? Sign in with your passkey to continue.",
                isSelected = selectedOption == CloudSetupOption.SIGN_IN,
                onClick = { onOptionSelected(CloudSetupOption.SIGN_IN) },
                modifier = Modifier.testTag(CLOUD_ACCOUNT_SETUP_SIGN_IN_OPTION_TAG),
            )
        }

        item {
            CloudSetupOptionCard(
                icon = Icons.Default.CloudOff,
                title = "Continue Without Cloud Sync",
                description = "Keep using LogDate locally on this device only. You can set up cloud sync later in Settings.",
                isSelected = selectedOption == CloudSetupOption.SKIP,
                onClick = { onOptionSelected(CloudSetupOption.SKIP) },
                modifier = Modifier.testTag(CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG),
            )
        }

        // Storage plans (shown when create account is selected)
        if (selectedOption == CloudSetupOption.CREATE_ACCOUNT) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                Text(
                    text = stringResource(Res.string.choose_your_storage_plan),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                PlanOptionCard(
                    option = LogDateBackupPlanOption.BASIC,
                    title = "Basic (Free)",
                    description = "Up to 10 GB of storage for text, photos, and videos. High-quality compression (1080p).",
                    price = "Free",
                    isRecommended = true,
                    onPlanSelected = onPlanSelected,
                )
            }

            item {
                PlanOptionCard(
                    option = LogDateBackupPlanOption.STANDARD,
                    title = "Premium",
                    description = "Up to 2 TB of storage with original quality photos and videos. Priority support.",
                    price = "$12/month",
                    isRecommended = false,
                    onPlanSelected = onPlanSelected,
                )
            }
        }

        // Action buttons
        item {
            Spacer(modifier = Modifier.height(Spacing.lg))

            when (selectedOption) {
                CloudSetupOption.CREATE_ACCOUNT -> {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(Res.string.create_account))
                    }
                }
                CloudSetupOption.SIGN_IN -> {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(Res.string.sign_in))
                    }
                }
                CloudSetupOption.SKIP -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG),
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
                null -> {
                    Text(
                        text = stringResource(Res.string.select_an_option_above_to_continue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSetupOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            if (isSelected) {
                CardDefaults.outlinedCardBorder(enabled = true).copy(
                    brush =
                        androidx.compose.foundation
                            .BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                            ).brush,
                )
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(32.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(Res.string.selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    option: LogDateBackupPlanOption,
    title: String,
    description: String,
    price: String,
    isRecommended: Boolean,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
) {
    Card(
        onClick = { onPlanSelected(option) },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isRecommended) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text(
                            text = stringResource(Res.string.recommended),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = price,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

enum class CloudSetupOption {
    CREATE_ACCOUNT,
    SIGN_IN,
    SKIP,
}

private enum class CloudSetupFlow {
    CREATE_ACCOUNT,
    SIGN_IN,
}

@Preview
@Composable
private fun CloudAccountSetupScreenPreview() {
    MaterialTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = CloudSetupOption.CREATE_ACCOUNT,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
        )
    }
}
