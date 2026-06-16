@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.rememberNotificationPermissionState
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val ONBOARDING_NOTIFICATIONS_ROOT_TAG = "onboarding_notifications_root"
const val ONBOARDING_NOTIFICATIONS_PRIMARY_TAG = "onboarding_notifications_primary"
const val ONBOARDING_NOTIFICATIONS_SKIP_TAG = "onboarding_notifications_skip"

@Composable
fun OnboardingNotificationsScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val recommendationsEnabled by viewModel.recommendationsEnabled.collectAsState()
    val permissionState = rememberNotificationPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val hasDecision = permissionState.hasPermission || permissionState.permissionRequested

    OnboardingNotificationsContent(
        onBack = onBack,
        onPrimaryAction = {
            if (hasDecision) {
                coroutineScope.launch {
                    isSaving = true
                    errorMessage = null
                    viewModel
                        .markNotificationsHandled()
                        .onSuccess {
                            isSaving = false
                            onNext()
                        }.onFailure {
                            errorMessage = getString(Res.string.onboarding_error_save_notifications)
                            isSaving = false
                        }
                }
            } else {
                permissionState.requestPermission()
            }
        },
        onSkip = {
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .markNotificationsHandled()
                    .onSuccess {
                        isSaving = false
                        onNext()
                    }.onFailure {
                        errorMessage = getString(Res.string.onboarding_error_save_notifications)
                        isSaving = false
                    }
            }
        },
        recommendationsEnabled = recommendationsEnabled,
        hasDecision = hasDecision,
        hasPermission = permissionState.hasPermission,
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@Composable
fun OnboardingNotificationsContent(
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit,
    recommendationsEnabled: Boolean,
    hasDecision: Boolean,
    hasPermission: Boolean,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    val bodyText =
        if (recommendationsEnabled) {
            stringResource(Res.string.onboarding_notifications_body_with_recommendations)
        } else {
            stringResource(Res.string.onboarding_notifications_body_without_recommendations)
        }
    val primaryActionLabel =
        if (hasDecision) {
            stringResource(UiRes.string.common_continue)
        } else {
            stringResource(Res.string.onboarding_notifications_enable)
        }

    Scaffold { contentPadding ->
        FoldableTabletopLayout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            minPaneHeight = 220.dp,
            topPane = {
                NotificationsBodyPane(
                    bodyText = bodyText,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            bottomPane = {
                NotificationsActionsPane(
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    onSkip = onSkip,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            standardContent = {
                FoldableBookLayout(
                    modifier = Modifier.fillMaxSize(),
                    minPaneWidth = 320.dp,
                    startPane = {
                        NotificationsBodyPane(
                            bodyText = bodyText,
                            onBack = onBack,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    endPane = {
                        NotificationsActionsPane(
                            primaryActionLabel = primaryActionLabel,
                            onPrimaryAction = onPrimaryAction,
                            onSkip = onSkip,
                            isSaving = isSaving,
                            errorMessage = errorMessage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    standardContent = {
                        NotificationsStandardContent(
                            bodyText = bodyText,
                            primaryActionLabel = primaryActionLabel,
                            onBack = onBack,
                            onPrimaryAction = onPrimaryAction,
                            onSkip = onSkip,
                            isSaving = isSaving,
                            errorMessage = errorMessage,
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun NotificationsStandardContent(
    bodyText: String,
    primaryActionLabel: String,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NotificationsBodyPane(
            bodyText = bodyText,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        NotificationsActionsPane(
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            onSkip = onSkip,
            isSaving = isSaving,
            errorMessage = errorMessage,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationsBodyPane(
    bodyText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .testTag(ONBOARDING_NOTIFICATIONS_ROOT_TAG)
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
                    stringResource(Res.string.onboarding_notifications_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
                Text(
                    bodyText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun NotificationsActionsPane(
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 444.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_NOTIFICATIONS_PRIMARY_TAG),
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(primaryActionLabel)
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.testTag(ONBOARDING_NOTIFICATIONS_SKIP_TAG)) {
                Text(stringResource(Res.string.onboarding_notifications_not_now))
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
}

@Preview
@Composable
private fun OnboardingNotificationsScreenPreview_WithRecommendations() {
    LogDateTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onPrimaryAction = {},
            onSkip = {},
            recommendationsEnabled = true,
            hasDecision = false,
            hasPermission = false,
        )
    }
}

@Preview
@Composable
private fun OnboardingNotificationsScreenPreview_WithoutRecommendations() {
    LogDateTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onPrimaryAction = {},
            onSkip = {},
            recommendationsEnabled = false,
            hasDecision = true,
            hasPermission = false,
        )
    }
}
