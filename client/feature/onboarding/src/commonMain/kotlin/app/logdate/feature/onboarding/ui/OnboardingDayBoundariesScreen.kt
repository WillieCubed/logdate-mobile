@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.permissions.rememberHealthConnectPermissionState
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingDayBoundariesScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val healthConnectStatus by viewModel.healthConnectStatus.collectAsState()
    val permissionState = rememberHealthConnectPermissionState()

    LaunchedEffect(permissionState.completedRequestCount) {
        if (permissionState.completedRequestCount > 0) {
            viewModel.refreshHealthStatus()
        }
    }

    LaunchedEffect(healthConnectStatus) {
        if (healthConnectStatus == HealthConnectStatus.NOT_AVAILABLE) {
            viewModel.disableSleepBasedDayBoundaries()
            onNext()
        }
    }

    LaunchedEffect(permissionState.completedRequestCount, healthConnectStatus) {
        if (permissionState.completedRequestCount > 0 && healthConnectStatus == HealthConnectStatus.CONNECTED) {
            viewModel.enableSleepBasedDayBoundaries()
            onNext()
        }
    }

    OnboardingDayBoundariesContent(
        healthConnectStatus = healthConnectStatus,
        onBack = onBack,
        onEnable = {
            when (healthConnectStatus) {
                HealthConnectStatus.CONNECTED -> {
                    viewModel.enableSleepBasedDayBoundaries()
                    onNext()
                }

                HealthConnectStatus.PERMISSIONS_NEEDED -> permissionState.requestPermission()
                HealthConnectStatus.CHECKING -> Unit
                HealthConnectStatus.NOT_AVAILABLE -> {
                    viewModel.disableSleepBasedDayBoundaries()
                    onNext()
                }
            }
        },
        onSkip = {
            viewModel.disableSleepBasedDayBoundaries()
            onNext()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDayBoundariesContent(
    healthConnectStatus: HealthConnectStatus,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)
    val primaryActionLabel =
        when (healthConnectStatus) {
            HealthConnectStatus.CONNECTED -> stringResource(Res.string.onboarding_day_boundaries_enable)
            HealthConnectStatus.PERMISSIONS_NEEDED -> stringResource(Res.string.onboarding_day_boundaries_grant_access)
            HealthConnectStatus.CHECKING -> stringResource(Res.string.onboarding_day_boundaries_status_checking_title)
            HealthConnectStatus.NOT_AVAILABLE -> stringResource(Res.string.onboarding_day_boundaries_not_now)
        }
    val statusTitle =
        when (healthConnectStatus) {
            HealthConnectStatus.CONNECTED -> stringResource(Res.string.onboarding_day_boundaries_status_connected_title)
            HealthConnectStatus.PERMISSIONS_NEEDED -> stringResource(Res.string.onboarding_day_boundaries_status_permissions_title)
            HealthConnectStatus.CHECKING -> stringResource(Res.string.onboarding_day_boundaries_status_checking_title)
            HealthConnectStatus.NOT_AVAILABLE -> stringResource(Res.string.onboarding_day_boundaries_status_checking_title)
        }
    val statusDescription =
        when (healthConnectStatus) {
            HealthConnectStatus.CONNECTED -> stringResource(Res.string.onboarding_day_boundaries_status_connected_description)
            HealthConnectStatus.PERMISSIONS_NEEDED -> stringResource(Res.string.onboarding_day_boundaries_status_permissions_description)
            HealthConnectStatus.CHECKING -> stringResource(Res.string.onboarding_day_boundaries_status_checking_description)
            HealthConnectStatus.NOT_AVAILABLE -> stringResource(Res.string.onboarding_day_boundaries_status_checking_description)
        }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(Res.string.onboarding_day_boundaries_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 444.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = contentPadding.calculateTopPadding() + Spacing.lg,
                    bottom = contentPadding.calculateBottomPadding() + Spacing.lg,
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr) + Spacing.lg,
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr) + Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    stringResource(Res.string.onboarding_day_boundaries_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_day_boundaries_card_title),
                    description = stringResource(Res.string.onboarding_day_boundaries_card_description),
                    icon = {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null)
                    },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = statusDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = stringResource(Res.string.onboarding_day_boundaries_privacy_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(Res.string.onboarding_day_boundaries_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = onEnable,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = healthConnectStatus != HealthConnectStatus.CHECKING,
                    ) {
                        Text(primaryActionLabel)
                    }
                    TextButton(onClick = onSkip) {
                        Text(stringResource(Res.string.onboarding_day_boundaries_not_now))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingDayBoundariesScreenPreview_PermissionsNeeded() {
    LogDateTheme {
        OnboardingDayBoundariesContent(
            healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingDayBoundariesScreenPreview_Connected() {
    LogDateTheme {
        OnboardingDayBoundariesContent(
            healthConnectStatus = HealthConnectStatus.CONNECTED,
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}
