@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.rememberNotificationPermissionState
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingNotificationsScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val recommendationsEnabled by viewModel.recommendationsEnabled.collectAsState()
    val permissionState = rememberNotificationPermissionState()

    // Auto-advance after permission dialog result
    LaunchedEffect(permissionState.permissionRequested) {
        if (permissionState.permissionRequested) {
            onNext()
        }
    }

    OnboardingNotificationsContent(
        onBack = onBack,
        onEnable = permissionState.requestPermission,
        onSkip = onNext,
        recommendationsEnabled = recommendationsEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNotificationsContent(
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    recommendationsEnabled: Boolean,
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)

    val bodyText =
        if (recommendationsEnabled) {
            stringResource(Res.string.onboarding_notifications_body_with_recommendations)
        } else {
            stringResource(Res.string.onboarding_notifications_body_without_recommendations)
        }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(Res.string.onboarding_notifications_title))
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
                    bodyText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = onEnable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.onboarding_notifications_enable))
                    }
                    TextButton(onClick = onSkip) {
                        Text(stringResource(Res.string.onboarding_notifications_not_now))
                    }
                }
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
            onEnable = {},
            onSkip = {},
            recommendationsEnabled = true,
        )
    }
}

@Preview
@Composable
private fun OnboardingNotificationsScreenPreview_WithoutRecommendations() {
    LogDateTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onEnable = {},
            onSkip = {},
            recommendationsEnabled = false,
        )
    }
}
