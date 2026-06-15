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
import androidx.compose.material.icons.rounded.LocationOn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.rememberLocationPermissionState
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val ONBOARDING_LOCATION_ROOT_TAG = "onboarding_location_root"
const val ONBOARDING_LOCATION_ENABLE_TAG = "onboarding_location_enable"
const val ONBOARDING_LOCATION_SKIP_TAG = "onboarding_location_skip"

@Composable
fun OnboardingLocationScreen(
    onBack: () -> Unit,
    onNext: (Boolean) -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val permissionState = rememberLocationPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-advance when permission is granted
    LaunchedEffect(permissionState.hasPermission) {
        if (permissionState.hasPermission && permissionState.permissionRequested) {
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .persistLocationTrackingEnabled()
                    .onSuccess {
                        viewModel
                            .markLocationHandled()
                            .onSuccess {
                                isSaving = false
                                onNext(true)
                            }.onFailure {
                                errorMessage = getString(Res.string.onboarding_error_save_location)
                                isSaving = false
                            }
                    }.onFailure {
                        errorMessage = getString(Res.string.onboarding_error_save_location)
                        isSaving = false
                    }
            }
        }
    }

    OnboardingLocationContent(
        onBack = onBack,
        onEnable = permissionState.requestPermission,
        onSkip = {
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .markLocationHandled()
                    .onSuccess {
                        isSaving = false
                        onNext(false)
                    }.onFailure {
                        errorMessage = getString(Res.string.onboarding_error_save_location)
                        isSaving = false
                    }
            }
        },
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@Composable
fun OnboardingLocationContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    LocationAdaptiveContent(
        onBack = onBack,
        onEnable = onEnable,
        onSkip = onSkip,
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@Composable
private fun LocationAdaptiveContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
) {
    FoldableTabletopLayout(
        modifier = Modifier.fillMaxSize().testTag(ONBOARDING_LOCATION_ROOT_TAG),
        minPaneHeight = 260.dp,
        topPane = {
            LocationInfoPane(
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        },
        bottomPane = {
            LocationActionPane(
                onEnable = onEnable,
                onSkip = onSkip,
                isSaving = isSaving,
                errorMessage = errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
        },
        fallback = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize().testTag(ONBOARDING_LOCATION_ROOT_TAG),
                minPaneWidth = 320.dp,
                startPane = {
                    LocationInfoPane(
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    LocationActionPane(
                        onEnable = onEnable,
                        onSkip = onSkip,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    LocationCompactContent(
                        onBack = onBack,
                        onEnable = onEnable,
                        onSkip = onSkip,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                    )
                },
            )
        },
    )
}

@Composable
private fun LocationInfoPane(
    onBack: () -> Unit,
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
                stringResource(Res.string.onboarding_location_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            Text(
                stringResource(Res.string.onboarding_location_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            OverviewItem(
                title = stringResource(Res.string.onboarding_location_card_title),
                description = stringResource(Res.string.onboarding_location_card_description),
                icon = {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null)
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
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                ) {
                    Text(
                        text = "Privacy first",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        stringResource(Res.string.onboarding_location_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationActionPane(
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
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
            modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_LOCATION_ENABLE_TAG),
            enabled = !isSaving,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(Res.string.onboarding_location_enable))
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.testTag(ONBOARDING_LOCATION_SKIP_TAG)) {
            Text(stringResource(Res.string.onboarding_location_not_now))
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
private fun LocationCompactContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
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
                        modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_LOCATION_ENABLE_TAG),
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(Res.string.onboarding_location_enable))
                        }
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.testTag(ONBOARDING_LOCATION_SKIP_TAG)) {
                        Text(stringResource(Res.string.onboarding_location_not_now))
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
                        .testTag(ONBOARDING_LOCATION_ROOT_TAG)
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
                        stringResource(Res.string.onboarding_location_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    Text(
                        stringResource(Res.string.onboarding_location_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OverviewItem(
                        title = stringResource(Res.string.onboarding_location_card_title),
                        description = stringResource(Res.string.onboarding_location_card_description),
                        icon = {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null)
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
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                        ) {
                            Text(
                                text = "Privacy first",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                stringResource(Res.string.onboarding_location_privacy),
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
private fun OnboardingLocationScreenPreview() {
    LogDateTheme {
        OnboardingLocationContent(
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}
