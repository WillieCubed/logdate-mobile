@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.client.domain.dayboundary.HealthConnectMissingRequirement
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.domain.dayboundary.reduceHealthConnectGateState
import app.logdate.client.permissions.rememberHealthConnectPermissionState
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val ONBOARDING_DAY_BOUNDARIES_ROOT_TAG = "onboarding_day_boundaries_root"
const val ONBOARDING_DAY_BOUNDARIES_ENABLE_TAG = "onboarding_day_boundaries_enable"
const val ONBOARDING_DAY_BOUNDARIES_SKIP_TAG = "onboarding_day_boundaries_skip"

@Composable
fun OnboardingDayBoundariesScreen(
    onBack: () -> Unit,
    onNext: (Boolean) -> Unit,
    onSetUpHealthConnect: () -> Unit = {},
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val progressSnapshot by viewModel.progressSnapshot.collectAsState()
    val healthConnectStatus = progressSnapshot.healthConnectStatus
    val permissionState = rememberHealthConnectPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingEnableAfterPermission by rememberSaveable { mutableStateOf(false) }
    var previousResolvedGateState by remember { mutableStateOf<HealthConnectGateState?>(null) }
    val gateState =
        reduceHealthConnectGateState(
            sleepBasedPreferenceEnabled = progressSnapshot.sleepBasedDayBoundariesEnabled,
            healthConnectStatus = healthConnectStatus,
            hasPermission = permissionState.hasPermission,
            permissionRequested = permissionState.permissionRequested,
            previousResolvedGateState = previousResolvedGateState,
        )

    fun persistAndContinue(enabled: Boolean) {
        coroutineScope.launch {
            isSaving = true
            errorMessage = null
            viewModel
                .persistSleepBasedDayBoundariesEnabled(enabled = enabled)
                .onSuccess {
                    viewModel
                        .markDayBoundariesHandled()
                        .onSuccess {
                            isSaving = false
                            onNext(enabled)
                        }.onFailure {
                            errorMessage = getString(Res.string.onboarding_error_save_day_boundaries)
                            isSaving = false
                        }
                }.onFailure {
                    errorMessage = getString(Res.string.onboarding_error_save_day_boundaries)
                    isSaving = false
                }
        }
    }

    LaunchedEffect(gateState) {
        if (gateState.kind != HealthConnectGateKind.CHECKING) {
            previousResolvedGateState = gateState
        }
        Napier.i(
            "Onboarding day boundaries gate state: kind=${gateState.kind} requirement=${gateState.missingRequirement}",
        )
    }

    LaunchedEffect(healthConnectStatus) {
        permissionState.refreshPermissionState()
    }

    LaunchedEffect(permissionState.hasPermission, healthConnectStatus) {
        val backendThinksConnected = healthConnectStatus == HealthConnectStatus.CONNECTED
        if (permissionState.hasPermission != backendThinksConnected) {
            viewModel.refreshHealthStatus()
        }
    }

    LaunchedEffect(healthConnectStatus) {
        if (healthConnectStatus == HealthConnectStatus.NOT_AVAILABLE) {
            persistAndContinue(enabled = false)
        }
    }

    LaunchedEffect(
        pendingEnableAfterPermission,
        permissionState.hasPermission,
        permissionState.permissionRequested,
        permissionState.isRequestInFlight,
        gateState,
    ) {
        when (
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = pendingEnableAfterPermission,
                hasPermission = permissionState.hasPermission,
                permissionRequested = permissionState.permissionRequested,
                isRequestInFlight = permissionState.isRequestInFlight,
                gateState = gateState,
            )
        ) {
            DayBoundariesPostPermissionAction.ENABLE_AND_CONTINUE -> {
                pendingEnableAfterPermission = false
                persistAndContinue(enabled = true)
            }

            DayBoundariesPostPermissionAction.RESET_REQUEST_STATE -> {
                pendingEnableAfterPermission = false
            }

            DayBoundariesPostPermissionAction.NONE -> Unit
        }
    }

    OnboardingDayBoundariesContent(
        gateState = gateState,
        onBack = onBack,
        onEnable = {
            when {
                gateState.kind == HealthConnectGateKind.READY -> {
                    persistAndContinue(enabled = true)
                }
                gateState.missingRequirement == HealthConnectMissingRequirement.PERMISSION -> {
                    pendingEnableAfterPermission = true
                    permissionState.requestPermission()
                }
                gateState.missingRequirement == HealthConnectMissingRequirement.SETUP -> {
                    onSetUpHealthConnect()
                }
                else -> Unit
            }
        },
        onSkip = {
            pendingEnableAfterPermission = false
            persistAndContinue(enabled = false)
        },
        isSaving = isSaving,
        isRequestInFlight = permissionState.isRequestInFlight,
        errorMessage = errorMessage,
    )
}

internal enum class DayBoundariesPostPermissionAction {
    ENABLE_AND_CONTINUE,
    RESET_REQUEST_STATE,
    NONE,
}

internal fun resolveDayBoundariesPostPermissionAction(
    pendingEnableAfterPermission: Boolean,
    hasPermission: Boolean,
    permissionRequested: Boolean,
    isRequestInFlight: Boolean,
    gateState: HealthConnectGateState,
): DayBoundariesPostPermissionAction {
    if (!pendingEnableAfterPermission) {
        return DayBoundariesPostPermissionAction.NONE
    }

    return when {
        hasPermission && gateState.kind == HealthConnectGateKind.READY -> {
            DayBoundariesPostPermissionAction.ENABLE_AND_CONTINUE
        }
        permissionRequested && !hasPermission && !isRequestInFlight -> {
            DayBoundariesPostPermissionAction.RESET_REQUEST_STATE
        }
        else -> DayBoundariesPostPermissionAction.NONE
    }
}

@Composable
fun OnboardingDayBoundariesContent(
    gateState: HealthConnectGateState,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean = false,
    isRequestInFlight: Boolean = false,
    errorMessage: String? = null,
) {
    val primaryActionLabel =
        when {
            gateState.kind == HealthConnectGateKind.READY -> {
                stringResource(Res.string.onboarding_day_boundaries_enable)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.PERMISSION -> {
                stringResource(Res.string.onboarding_day_boundaries_grant_access)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.SETUP -> {
                stringResource(Res.string.onboarding_day_boundaries_set_up_health_connect)
            }
            else -> stringResource(Res.string.onboarding_day_boundaries_status_checking_title)
        }
    val statusTitle =
        when {
            gateState.kind == HealthConnectGateKind.READY -> {
                stringResource(Res.string.onboarding_day_boundaries_status_connected_title)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.PERMISSION -> {
                stringResource(Res.string.onboarding_day_boundaries_status_permissions_title)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.SETUP -> {
                stringResource(Res.string.onboarding_day_boundaries_status_setup_required_title)
            }
            else -> stringResource(Res.string.onboarding_day_boundaries_status_checking_title)
        }
    val statusDescription =
        when {
            gateState.kind == HealthConnectGateKind.READY -> {
                stringResource(Res.string.onboarding_day_boundaries_status_connected_description)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.PERMISSION -> {
                stringResource(Res.string.onboarding_day_boundaries_status_permissions_description)
            }
            gateState.missingRequirement == HealthConnectMissingRequirement.SETUP -> {
                stringResource(Res.string.onboarding_day_boundaries_status_setup_required_description)
            }
            else -> stringResource(Res.string.onboarding_day_boundaries_status_checking_description)
        }

    DayBoundariesAdaptiveContent(
        gateState = gateState,
        onBack = onBack,
        onEnable = onEnable,
        onSkip = onSkip,
        isSaving = isSaving,
        isRequestInFlight = isRequestInFlight,
        errorMessage = errorMessage,
        statusTitle = statusTitle,
        statusDescription = statusDescription,
        primaryActionLabel = primaryActionLabel,
    )
}

@Composable
private fun DayBoundariesAdaptiveContent(
    gateState: HealthConnectGateState,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    isRequestInFlight: Boolean,
    errorMessage: String?,
    statusTitle: String,
    statusDescription: String,
    primaryActionLabel: String,
) {
    FoldableTabletopLayout(
        modifier = Modifier.fillMaxSize().testTag(ONBOARDING_DAY_BOUNDARIES_ROOT_TAG),
        minPaneHeight = 260.dp,
        topPane = {
            DayBoundariesInfoPane(
                onBack = onBack,
                statusTitle = statusTitle,
                statusDescription = statusDescription,
                modifier = Modifier.fillMaxSize(),
            )
        },
        bottomPane = {
            DayBoundariesActionPane(
                gateState = gateState,
                onEnable = onEnable,
                onSkip = onSkip,
                isSaving = isSaving,
                isRequestInFlight = isRequestInFlight,
                errorMessage = errorMessage,
                primaryActionLabel = primaryActionLabel,
                modifier = Modifier.fillMaxSize(),
            )
        },
        standardContent = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize().testTag(ONBOARDING_DAY_BOUNDARIES_ROOT_TAG),
                minPaneWidth = 320.dp,
                startPane = {
                    DayBoundariesInfoPane(
                        onBack = onBack,
                        statusTitle = statusTitle,
                        statusDescription = statusDescription,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    DayBoundariesActionPane(
                        gateState = gateState,
                        onEnable = onEnable,
                        onSkip = onSkip,
                        isSaving = isSaving,
                        isRequestInFlight = isRequestInFlight,
                        errorMessage = errorMessage,
                        primaryActionLabel = primaryActionLabel,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    DayBoundariesCompactContent(
                        onBack = onBack,
                        onEnable = onEnable,
                        onSkip = onSkip,
                        isSaving = isSaving,
                        isRequestInFlight = isRequestInFlight,
                        errorMessage = errorMessage,
                        statusTitle = statusTitle,
                        statusDescription = statusDescription,
                        primaryActionLabel = primaryActionLabel,
                    )
                },
            )
        },
    )
}

@Composable
private fun DayBoundariesInfoPane(
    onBack: () -> Unit,
    statusTitle: String,
    statusDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(UiRes.string.common_back))
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 444.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(Res.string.onboarding_day_boundaries_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            Text(
                stringResource(Res.string.onboarding_day_boundaries_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            OverviewItem(
                title = stringResource(Res.string.onboarding_day_boundaries_card_title),
                description = stringResource(Res.string.onboarding_day_boundaries_card_description),
                icon = {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null)
                },
            )
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
    }
}

@Composable
private fun DayBoundariesActionPane(
    gateState: HealthConnectGateState,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    isRequestInFlight: Boolean,
    errorMessage: String?,
    primaryActionLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_DAY_BOUNDARIES_ENABLE_TAG),
            enabled = !isSaving && !isRequestInFlight && gateState.kind != HealthConnectGateKind.CHECKING,
        ) {
            if (isSaving || isRequestInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(primaryActionLabel)
            }
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag(ONBOARDING_DAY_BOUNDARIES_SKIP_TAG),
            enabled = !isSaving,
        ) {
            Text(stringResource(Res.string.onboarding_day_boundaries_not_now))
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DayBoundariesCompactContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    isRequestInFlight: Boolean,
    errorMessage: String?,
    statusTitle: String,
    statusDescription: String,
    primaryActionLabel: String,
) {
    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = onEnable,
                        modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_DAY_BOUNDARIES_ENABLE_TAG),
                        enabled = !isSaving && !isRequestInFlight,
                    ) {
                        if (isSaving || isRequestInFlight) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(primaryActionLabel)
                        }
                    }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.testTag(ONBOARDING_DAY_BOUNDARIES_SKIP_TAG),
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(Res.string.onboarding_day_boundaries_not_now))
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .testTag(ONBOARDING_DAY_BOUNDARIES_ROOT_TAG)
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
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(UiRes.string.common_back))
                    }
                }
                Column(
                    modifier = Modifier.widthIn(max = 444.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_day_boundaries_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    Text(
                        stringResource(Res.string.onboarding_day_boundaries_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OverviewItem(
                        title = stringResource(Res.string.onboarding_day_boundaries_card_title),
                        description = stringResource(Res.string.onboarding_day_boundaries_card_description),
                        icon = {
                            Icon(Icons.Rounded.Bedtime, contentDescription = null)
                        },
                    )
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
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingDayBoundariesScreenPreview_PermissionsNeeded() {
    LogDateTheme {
        OnboardingDayBoundariesContent(
            gateState =
                HealthConnectGateState(
                    kind = HealthConnectGateKind.NEEDS_PERMISSION,
                    missingRequirement = HealthConnectMissingRequirement.PERMISSION,
                ),
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
            gateState =
                HealthConnectGateState(
                    kind = HealthConnectGateKind.READY,
                ),
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}
