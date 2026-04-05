@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.rememberLocationPermissionState
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
                                errorMessage = "We couldn't save your location preference right now."
                                isSaving = false
                            }
                    }.onFailure {
                        errorMessage = "We couldn't save your location preference right now."
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
                        errorMessage = "We couldn't save your location preference right now."
                        isSaving = false
                    }
            }
        },
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingLocationContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(Res.string.onboarding_location_title))
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
                    .testTag(ONBOARDING_LOCATION_ROOT_TAG)
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
                    stringResource(Res.string.onboarding_location_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_location_card_title),
                    description = stringResource(Res.string.onboarding_location_card_description),
                    icon = {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null)
                    },
                )
            }
            item {
                // Privacy card
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
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
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
