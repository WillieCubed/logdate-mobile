@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.client.domain.dayboundary.HealthConnectMissingRequirement
import app.logdate.feature.core.settings.ui.DayBoundarySettingsContent
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.components.RewindCoverCard
import app.logdate.feature.rewind.ui.detail.RewindDetailScreenContent
import app.logdate.feature.rewind.ui.detail.RewindErrorScreen
import app.logdate.feature.rewind.ui.detail.RewindLoadingScreen
import app.logdate.feature.rewind.ui.detail.RewindUpgradeChip
import app.logdate.feature.rewind.ui.overview.FirstRewindOnboardingSheetContent
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.rewind.ui.past.PastRewindsScreen
import app.logdate.feature.rewind.ui.settings.RewindSettingsContent

internal val catalogDesktopOnlyScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.RewindCoverCard, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            Box(modifier = Modifier.width(520.dp)) {
                RewindCoverCard(
                    rewind = rewindPreview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        sharedScene(SharedScreenshotSceneId.RewindOverviewCanonical, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            RewindScreenContent(
                state =
                    RewindOverviewScreenUiState.Ready(
                        pastRewinds = pastRewindSamples,
                        mostRecentRewind = rewindPreview,
                    ),
                onOpenRewind = {},
                onGenerateAnnualRewind = null,
            )
        },
        sharedScene(SharedScreenshotSceneId.RewindDetailPopulated, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            RewindDetailScreenContent(
                uiState = RewindDetailUiState.Success(panels = rewindDetailPanels),
                onExitRewind = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.RewindLoading, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            RewindLoadingScreen()
        },
        sharedScene(SharedScreenshotSceneId.RewindError, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            RewindErrorScreen(
                message = "This Rewind couldn't be loaded. Try again later.",
                onExit = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.PastRewinds, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            PastRewindsScreen(
                onGoBack = {},
                rewinds = pastRewindSamples,
                onOpenRewind = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.RewindSettings, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            RewindSettingsContent(
                autoGenerationEnabled = true,
                notificationsEnabled = true,
                reflectionRepliesEnabled = true,
                onAutoGenerationToggled = {},
                onNotificationsToggled = {},
                onReflectionRepliesToggled = {},
                onBack = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.RewindUpgradeChip, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(48.dp),
            ) {
                RewindUpgradeChip(onUpgradeClick = {})
            }
        },
        sharedScene(SharedScreenshotSceneId.FirstRewindOnboardingSheet, ScreenshotSceneGroup.REWIND, desktopOnlyVariants) {
            FirstRewindOnboardingSheetContent(onDismiss = {})
        },
        sharedScene(SharedScreenshotSceneId.DayBoundaryRecovery, ScreenshotSceneGroup.SETTINGS, desktopTallVariants) {
            DayBoundarySettingsContent(
                sleepBasedPreferenceEnabled = true,
                fallbackStartHour = 4,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.RECOVERY_REQUIRED,
                        missingRequirement = HealthConnectMissingRequirement.PERMISSION,
                    ),
                isRequestInFlight = false,
                onBack = {},
                onToggleSleepBased = {},
                onSetFallbackHour = {},
                onRequestPermissions = {},
                onSetUpHealthConnect = {},
                onOpenHealthConnectPermissions = {},
            )
        },
    )
