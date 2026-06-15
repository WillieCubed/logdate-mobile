package app.logdate.screenshots.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.client.domain.dayboundary.HealthConnectMissingRequirement
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.domain.recommendation.AmbientPromptTime
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.domain.streak.StreakData
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSyncSettings
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.feature.core.account.CloudAccountSignInContent
import app.logdate.feature.core.account.CloudAccountWelcomeContent
import app.logdate.feature.core.account.PasskeyAccountCreationFinalContent
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.settings.ui.AccountIdentityState
import app.logdate.feature.core.settings.ui.AccountSettingsContent
import app.logdate.feature.core.settings.ui.AdvancedSettingsContent
import app.logdate.feature.core.settings.ui.BirthdaySettingsContent
import app.logdate.feature.core.settings.ui.ConflictsState
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.DayBoundarySettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.LibrarySettingsContent
import app.logdate.feature.core.settings.ui.MemoriesSettingsContent
import app.logdate.feature.core.settings.ui.MemoriesWidgetInstallUiState
import app.logdate.feature.core.settings.ui.PasskeyInfo
import app.logdate.feature.core.settings.ui.PrivacySettingsContent
import app.logdate.feature.core.settings.ui.RecommendationSettingsContent
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.StorageCategory
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.settings.ui.StreakSettingsContent
import app.logdate.feature.core.settings.ui.TimelineSettingsContent
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.feature.core.settings.ui.devices.DeviceInfoUiState
import app.logdate.feature.core.settings.ui.devices.DevicesScreenContent
import app.logdate.feature.core.settings.ui.devices.DevicesUiState
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchSyncSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingContent
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionUiState
import app.logdate.feature.onboarding.ui.OnboardingBirthdayContent
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesContent
import app.logdate.feature.onboarding.ui.OnboardingLocationContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationsContent
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.feature.rewind.ui.BasicTextRewindPanelUiState
import app.logdate.feature.rewind.ui.HighlightedQuoteRewindPanelUiState
import app.logdate.feature.rewind.ui.NarrativeContextRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.components.RewindCoverCard
import app.logdate.feature.rewind.ui.detail.RewindDetailScreenContent
import app.logdate.feature.rewind.ui.detail.RewindErrorScreen
import app.logdate.feature.rewind.ui.detail.RewindLoadingScreen
import app.logdate.feature.rewind.ui.detail.RewindUpgradeChip
import app.logdate.feature.rewind.ui.overview.FirstRewindOnboardingSheetContent
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.feature.rewind.ui.past.PastRewindsScreen
import app.logdate.feature.rewind.ui.settings.RewindSettingsContent
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class SharedScreenshotSceneId(
    val value: String,
) {
    OnboardingStartSplash("onboarding-start-splash"),
    OnboardingStartLanding("onboarding-start-landing"),
    PersonalIntroName("personal-intro-name"),
    PersonalIntroBio("personal-intro-bio"),
    OnboardingOverview("onboarding-overview"),
    MemoriesImportInfo("memories-import-info"),
    MemorySelectionPopulated("memory-selection-populated"),
    CloudAccountSetupCompact("cloud-account-setup-compact"),
    OnboardingBirthday("onboarding-birthday"),
    OnboardingRecommendations("onboarding-recommendations"),
    OnboardingDayBoundariesConnected("onboarding-day-boundaries-connected"),
    OnboardingLocation("onboarding-location"),
    OnboardingNotifications("onboarding-notifications"),
    OnboardingCompletionStreak("onboarding-completion-streak"),
    OnboardingCompletionFinal("onboarding-completion-final"),
    OnboardingWelcomeBack("onboarding-welcome-back"),
    MemorySelectionEmpty("memory-selection-empty"),
    MemorySelectionError("memory-selection-error"),
    MemorySelectionLoading("memory-selection-loading"),
    RecommendationsSaving("recommendations-saving"),
    DayBoundariesPermissionsNeeded("day-boundaries-permissions-needed"),
    DayBoundariesChecking("day-boundaries-checking"),
    NotificationsDecisionHandled("notifications-decision-handled"),
    CloudAccountSelectedSignIn("cloud-account-selected-sign-in"),
    CloudAccountAdaptiveLargeScreen("cloud-account-adaptive-large-screen"),
    MemorySelectionAdaptiveLargeScreen("memory-selection-adaptive-large-screen"),
    CloudAccountWelcome("cloud-account-welcome"),
    CloudAccountSignIn("cloud-account-sign-in"),
    PasskeyAccountCreationFinal("passkey-account-creation-final"),
    SettingsOverview("settings-overview"),
    AccountSettings("account-settings"),
    PrivacySettings("privacy-settings"),
    DataSettings("data-settings"),
    MemoriesSettings("memories-settings"),
    DevicesSettings("devices-settings"),
    StreakSettings("streak-settings"),
    TimelineSettings("timeline-settings"),
    DayBoundarySettings("day-boundary-settings"),
    LibrarySettings("library-settings"),
    RecommendationSettings("recommendation-settings"),
    BirthdaySettings("birthday-settings"),
    AdvancedSettings("advanced-settings"),
    WatchSettings("watch-settings"),
    WatchSyncSettings("watch-sync-settings"),
    WatchNotificationSettings("watch-notification-settings"),
    WatchTroubleshooting("watch-troubleshooting"),
    SearchIdle("search-idle"),
    SearchSearching("search-searching"),
    SearchEmpty("search-empty"),
    SearchResults("search-results"),
    RewindCoverCard("rewind-cover-card"),
    RewindOverviewCanonical("rewind-overview-canonical"),
    RewindDetailPopulated("rewind-detail-populated"),
    RewindLoading("rewind-loading"),
    RewindError("rewind-error"),
    PastRewinds("past-rewinds"),
    RewindSettings("rewind-settings"),
    RewindUpgradeChip("rewind-upgrade-chip"),
    FirstRewindOnboardingSheet("first-rewind-onboarding-sheet"),
    DayBoundaryRecovery("day-boundary-recovery"),
}

enum class ScreenshotSceneGroup {
    ONBOARDING,
    SEARCH,
    REWIND,
    SETTINGS,
    SECURITY,
}

enum class ScreenshotViewportId(
    val baselineSuffix: String,
    val widthDp: Int,
    val heightDp: Int,
    val darkTheme: Boolean = false,
) {
    PHONE_LIGHT("phone", 411, 891),
    PHONE_DARK("phone-dark", 411, 891, darkTheme = true),
    PHONE_LANDSCAPE("phone-landscape", 891, 411),
    TABLET("tablet", 1280, 800),
    SPLIT_MEDIUM("split-medium", 700, 900),
    TABLET_PORTRAIT("tablet-portrait", 800, 1280),
    DESKTOP_WINDOW("desktop-window", 1440, 900),
    DESKTOP("desktop", 1280, 800),
    DESKTOP_TALL("desktop-tall", 1280, 900),
    DESKTOP_WIDE("desktop-wide", 1366, 900),
}

data class ScreenshotSceneVariant(
    val viewport: ScreenshotViewportId,
)

data class SharedScreenshotSceneSpec(
    val id: SharedScreenshotSceneId,
    val group: ScreenshotSceneGroup,
    val variants: List<ScreenshotSceneVariant>,
    val content: @Composable () -> Unit,
)

private val standardMatrixVariants =
    listOf(
        ScreenshotSceneVariant(ScreenshotViewportId.PHONE_LIGHT),
        ScreenshotSceneVariant(ScreenshotViewportId.PHONE_DARK),
        ScreenshotSceneVariant(ScreenshotViewportId.PHONE_LANDSCAPE),
        ScreenshotSceneVariant(ScreenshotViewportId.TABLET),
    )

private val largeScreenAuditVariants =
    listOf(
        ScreenshotSceneVariant(ScreenshotViewportId.SPLIT_MEDIUM),
        ScreenshotSceneVariant(ScreenshotViewportId.TABLET),
        ScreenshotSceneVariant(ScreenshotViewportId.TABLET_PORTRAIT),
        ScreenshotSceneVariant(ScreenshotViewportId.DESKTOP_WINDOW),
    )

private val desktopOnlyVariants =
    listOf(
        ScreenshotSceneVariant(ScreenshotViewportId.DESKTOP),
    )

private val desktopTallVariants =
    listOf(
        ScreenshotSceneVariant(ScreenshotViewportId.DESKTOP_TALL),
    )

private val desktopWideVariants =
    listOf(
        ScreenshotSceneVariant(ScreenshotViewportId.DESKTOP_WIDE),
    )

object SharedScreenshotCatalog {
    val onboardingMainScenes: List<SharedScreenshotSceneSpec> =
        listOf(
            sharedScene(SharedScreenshotSceneId.OnboardingStartSplash, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingStartScreenContent(
                    showLanding = false,
                    onGetStarted = {},
                    onStartFromBackup = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingStartLanding, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingStartScreenContent(
                    showLanding = true,
                    onGetStarted = {},
                    onStartFromBackup = {},
                    animateContent = false,
                )
            },
            sharedScene(SharedScreenshotSceneId.PersonalIntroName, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                PersonalIntroContent(
                    uiState =
                        PersonalIntroUiState(
                            name = "Alex",
                            currentStep = PersonalIntroStep.Name,
                        ),
                    onNameChanged = {},
                    onBioChanged = {},
                    onProceedToBio = {},
                    onGoBackToName = {},
                    onProcessWithLlm = {},
                    onBack = {},
                    autoFocusInputs = false,
                    animateStepTransitions = false,
                )
            },
            sharedScene(SharedScreenshotSceneId.PersonalIntroBio, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                PersonalIntroContent(
                    uiState =
                        PersonalIntroUiState(
                            name = "Alex",
                            bio = "I keep a private timeline of the people, places, and moments that matter.",
                            currentStep = PersonalIntroStep.Bio,
                        ),
                    onNameChanged = {},
                    onBioChanged = {},
                    onProceedToBio = {},
                    onGoBackToName = {},
                    onProcessWithLlm = {},
                    onBack = {},
                    autoFocusInputs = false,
                    animateStepTransitions = false,
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingOverview, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingOverviewScreen(onBack = {}, onNext = {})
            },
            sharedScene(SharedScreenshotSceneId.MemoriesImportInfo, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                MemoriesImportInfoScreen(onBack = {}, onContinue = {})
            },
            sharedScene(SharedScreenshotSceneId.MemorySelectionPopulated, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                val memories = sampleMemories()
                MemorySelectionScreen(
                    uiState =
                        MemorySelectionUiState(
                            allMemories = memories,
                            aiCuratedMemories = memories.take(6),
                            selectedMemoryIds = setOf("sample1", "sample5"),
                            isLoading = false,
                            hasMoreMemories = true,
                        ),
                    onBack = {},
                    onContinue = {},
                    onToggleMemorySelection = {},
                    onLoadMoreMemories = {},
                    onRefreshMemories = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.CloudAccountSetupCompact, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                CloudAccountWelcomeContent(
                    onContinue = {},
                    onSignIn = {},
                    onSkip = {},
                    serverSelectionState = ServerSelectionState(),
                    onSelectServerPreset = {},
                    onCustomServerUrlChange = {},
                    onShowCustomServerInfo = {},
                    isPasskeySupported = true,
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingBirthday, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingBirthdayContent(
                    onBack = {},
                    onBirthdaySelected = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingRecommendations, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingRecommendationsContent(
                    onBack = {},
                    onKeepOn = {},
                    onTurnOff = {},
                )
            },
            sharedScene(
                SharedScreenshotSceneId.OnboardingDayBoundariesConnected,
                ScreenshotSceneGroup.ONBOARDING,
                standardMatrixVariants,
            ) {
                OnboardingDayBoundariesContent(
                    gateState = HealthConnectGateState(HealthConnectGateKind.READY),
                    onBack = {},
                    onEnable = {},
                    onSkip = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingLocation, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingLocationContent(
                    onBack = {},
                    onEnable = {},
                    onSkip = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingNotifications, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = {},
                    onSkip = {},
                    recommendationsEnabled = true,
                    hasDecision = false,
                    hasPermission = false,
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingCompletionStreak, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingCompletionContent(
                    shouldShowFinish = false,
                    onContinue = {},
                    onFinish = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingCompletionFinal, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingCompletionContent(
                    shouldShowFinish = true,
                    onContinue = {},
                    onFinish = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.OnboardingWelcomeBack, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                WelcomeBackScreenContent(name = "Alex")
            },
        )

    val onboardingExtendedScenes: List<SharedScreenshotSceneSpec> =
        listOf(
            sharedScene(SharedScreenshotSceneId.MemorySelectionEmpty, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                MemorySelectionScreen(
                    uiState = MemorySelectionUiState(isLoading = false, hasMoreMemories = false),
                    onBack = {},
                    onContinue = {},
                    onToggleMemorySelection = {},
                    onLoadMoreMemories = {},
                    onRefreshMemories = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.MemorySelectionError, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                MemorySelectionScreen(
                    uiState =
                        MemorySelectionUiState(
                            isLoading = false,
                            hasMoreMemories = false,
                            loadFailed = true,
                        ),
                    onBack = {},
                    onContinue = {},
                    onToggleMemorySelection = {},
                    onLoadMoreMemories = {},
                    onRefreshMemories = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.MemorySelectionLoading, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                MemorySelectionScreen(
                    uiState = MemorySelectionUiState(isLoading = true),
                    onBack = {},
                    onContinue = {},
                    onToggleMemorySelection = {},
                    onLoadMoreMemories = {},
                    onRefreshMemories = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.RecommendationsSaving, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingRecommendationsContent(
                    onBack = {},
                    onKeepOn = {},
                    onTurnOff = {},
                    isSaving = true,
                )
            },
            sharedScene(SharedScreenshotSceneId.DayBoundariesPermissionsNeeded, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
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
            },
            sharedScene(SharedScreenshotSceneId.DayBoundariesChecking, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingDayBoundariesContent(
                    gateState = HealthConnectGateState(HealthConnectGateKind.CHECKING),
                    onBack = {},
                    onEnable = {},
                    onSkip = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.NotificationsDecisionHandled, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = {},
                    onSkip = {},
                    recommendationsEnabled = false,
                    hasDecision = true,
                    hasPermission = false,
                )
            },
            sharedScene(SharedScreenshotSceneId.CloudAccountSelectedSignIn, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                CloudAccountSignInContent(
                    username = "alex",
                    onUsernameChange = {},
                    onSignIn = {},
                    onAccountRecovery = {},
                    onPrivacyPolicy = {},
                    onTermsOfService = {},
                    onBack = {},
                    serverDisplayName = "LogDate Cloud",
                    serverHandleDomain = "logdate.app",
                )
            },
            sharedScene(
                SharedScreenshotSceneId.CloudAccountAdaptiveLargeScreen,
                ScreenshotSceneGroup.ONBOARDING,
                largeScreenAuditVariants,
            ) {
                CloudAccountWelcomeContent(
                    onContinue = {},
                    onSignIn = {},
                    onSkip = {},
                    serverSelectionState = ServerSelectionState(),
                    onSelectServerPreset = {},
                    onCustomServerUrlChange = {},
                    onShowCustomServerInfo = {},
                    isPasskeySupported = true,
                )
            },
            sharedScene(
                SharedScreenshotSceneId.MemorySelectionAdaptiveLargeScreen,
                ScreenshotSceneGroup.ONBOARDING,
                largeScreenAuditVariants,
            ) {
                val memories = sampleMemories(videoEvery = 4, videoDuration = Duration.parse("45s"))
                MemorySelectionScreen(
                    uiState =
                        MemorySelectionUiState(
                            allMemories = memories,
                            aiCuratedMemories = memories.take(4),
                            selectedMemoryIds = setOf("sample1", "sample2", "sample7"),
                            isLoading = false,
                            hasMoreMemories = false,
                        ),
                    onBack = {},
                    onContinue = {},
                    onToggleMemorySelection = {},
                    onLoadMoreMemories = {},
                    onRefreshMemories = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.CloudAccountWelcome, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                CloudAccountWelcomeContent(
                    onContinue = {},
                    onSignIn = {},
                    onSkip = {},
                    serverSelectionState = ServerSelectionState(),
                    onSelectServerPreset = {},
                    onCustomServerUrlChange = {},
                    onShowCustomServerInfo = {},
                    isPasskeySupported = true,
                )
            },
            sharedScene(SharedScreenshotSceneId.CloudAccountSignIn, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                CloudAccountSignInContent(
                    username = "alex",
                    onUsernameChange = {},
                    onSignIn = {},
                    onAccountRecovery = {},
                    onPrivacyPolicy = {},
                    onTermsOfService = {},
                    onBack = {},
                    serverDisplayName = "LogDate Cloud",
                    serverHandleDomain = "logdate.app",
                )
            },
            sharedScene(SharedScreenshotSceneId.PasskeyAccountCreationFinal, ScreenshotSceneGroup.ONBOARDING, standardMatrixVariants) {
                PasskeyAccountCreationFinalContent(
                    displayName = "Alex Rivera",
                    username = "alex",
                    bio = "I keep a durable private memory archive of people, places, and milestones.",
                    onBioChange = {},
                    onCreateAccount = {},
                    onBack = {},
                    isCreatingAccount = false,
                    errorMessage = null,
                    onClearError = {},
                    isPasskeySupported = true,
                    handleDomain = "logdate.app",
                    serverDisplayName = "LogDate Cloud",
                )
            },
        )

    val settingsScenes: List<SharedScreenshotSceneSpec> =
        listOf(
            sharedScene(SharedScreenshotSceneId.SettingsOverview, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                SettingsOverviewContent(
                    onBack = {},
                    onNavigateToProfile = {},
                    onNavigateToAccount = {},
                    onNavigateToDevices = {},
                    onNavigateToWatch = {},
                    onNavigateToReset = {},
                    onNavigateToLocation = {},
                    onNavigateToPrivacy = {},
                    onNavigateToMemories = {},
                    onNavigateToVoiceNotes = {},
                    onNavigateToNotifications = {},
                    onNavigateToStreaks = {},
                    onNavigateToRewindSettings = {},
                    onNavigateToEventsSettings = {},
                    onNavigateToPeopleSettings = {},
                    onNavigateToTimeline = {},
                    onNavigateToSync = {},
                    onNavigateToExport = {},
                    onNavigateToCloudAccountCreation = {},
                    onNavigateToSignIn = {},
                    onNavigateToLibrarySettings = {},
                    userProfile = UserProfile(name = "Alex Rivera", username = "alex", isAuthenticated = true),
                    onboardedDate = Instant.DISTANT_PAST,
                    streakCount = 12,
                )
            },
            sharedScene(SharedScreenshotSceneId.AccountSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                AccountSettingsContent(
                    onBack = {},
                    onCreatePasskey = {},
                    userProfile = UserProfile(name = "Alex Rivera", username = "alex", isAuthenticated = true),
                    passkeys =
                        listOf(
                            PasskeyInfo(
                                id = "passkey-ios",
                                name = "Primary Passkey",
                                device = "iPhone 16 Pro",
                                lastUsed = baseInstant,
                            ),
                        ),
                    onRevokePasskey = {},
                    onSignOut = {},
                    identityState = AccountIdentityState(),
                    onRefreshIdentity = {},
                    onExportSigningKey = {},
                    onRotateSigningKey = {},
                    onImportSigningKey = { _, _ -> },
                    onImportSigningKeyWithRecovery = { _, _, _ -> },
                    onDerivePlcRecoveryKey = {},
                    onRegisterPlcRecoveryKey = {},
                    onRegisterDerivedPlcRecoveryKey = {},
                    onClearIdentityActionState = {},
                    onClearExportedKeyJson = {},
                    onClearDerivedRecoveryDidKey = {},
                    serverSelectionState = ServerSelectionState(),
                    onSelectServerPreset = {},
                    onUpdateCustomServerUrl = {},
                    onValidateAndSaveServer = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.PrivacySettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                PrivacySettingsContent(
                    onBack = {},
                    onSetBiometricsEnabled = {},
                    onSetSystemSearchVisibilityEnabled = {},
                    isBiometricsEnabled = true,
                    isAuthenticated = true,
                    isSystemSearchVisibilityEnabled = true,
                    showSystemSearchVisibilityToggle = true,
                    passkeys =
                        listOf(
                            PasskeyInfo(
                                id = "passkey-mac",
                                name = "MacBook Passkey",
                                device = "MacBook Pro",
                                lastUsed = baseInstant,
                            ),
                        ),
                    onCreatePasskey = {},
                    onRevokePasskey = {},
                    onNavigateToLocationSettings = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.DataSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                DataSettingsContent(
                    onBack = {},
                    quotaUsage =
                        StorageQuotaUi(
                            totalBytes = 5_000_000_000,
                            usedBytes = 2_650_000_000,
                            usagePercentage = 0.53f,
                            categories =
                                listOf(
                                    StorageCategory(
                                        name = "IMAGE_NOTES",
                                        usedBytes = 1_400_000_000,
                                        usagePercentage = 0.53f,
                                        color = Color(0xFF4CAF50),
                                        formattedUsed = "1.4 GB",
                                    ),
                                    StorageCategory(
                                        name = "TEXT_NOTES",
                                        usedBytes = 650_000_000,
                                        usagePercentage = 0.25f,
                                        color = Color(0xFF2196F3),
                                        formattedUsed = "650 MB",
                                    ),
                                ),
                            formattedTotal = "5.0 GB",
                            formattedUsed = "2.65 GB",
                        ),
                    isQuotaAvailable = true,
                    exportState = ExportState.Idle,
                    onShowExportOptions = {},
                    onUpdateExportOptions = {},
                    onConfirmExport = {},
                    onCancelExport = {},
                    onRetryExport = {},
                    onDismissExport = {},
                    onBrowseExport = {},
                    restoreState = RestoreState.Idle,
                    onShowRestoreSheet = {},
                    onSelectRestoreFile = {},
                    onUpdateImportOptions = {},
                    onConfirmImport = {},
                    onCancelRestore = {},
                    onRetryRestore = {},
                    onDismissRestore = {},
                    integrityState = IntegrityState(),
                    onRunIntegrityCheck = {},
                    onRepairIntegrity = {},
                    conflictsState = ConflictsState(),
                    onClearConflicts = {},
                    onRefreshConflicts = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    isAuthenticated = true,
                    onSyncNow = {},
                    isBackgroundSyncEnabled = true,
                    onBackgroundSyncEnabledChange = {},
                    onNavigateToCloudAccountCreation = {},
                    onNavigateToSignIn = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.MemoriesSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                MemoriesSettingsContent(
                    onBack = {},
                    onNavigateToRecommendations = {},
                    contextualRecommendationsEnabled = true,
                    onToggleContextualRecommendations = {},
                    recallMode = RecallMode.ON_THIS_DAY,
                    onSetRecallMode = {},
                    widgetInstallUiState = MemoriesWidgetInstallUiState.Available,
                    onAddWidgetToHomeScreen = {},
                    widgetContentTypes = setOf(WidgetContentType.TEXT, WidgetContentType.PHOTOS),
                    onToggleContentType = { _, _ -> },
                )
            },
            sharedScene(SharedScreenshotSceneId.DevicesSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                DevicesScreenContent(
                    onBackClick = {},
                    uiState =
                        DevicesUiState(
                            isLoading = false,
                            devices =
                                listOf(
                                    DeviceInfoUiState(
                                        id = Uuid.parse("10000000-0000-0000-0000-000000000001"),
                                        name = "Alex's Pixel 9 Pro",
                                        platformName = "Android",
                                        lastActiveFormatted = "Today",
                                        appVersion = "1.0.0",
                                        isCurrentDevice = true,
                                    ),
                                    DeviceInfoUiState(
                                        id = Uuid.parse("10000000-0000-0000-0000-000000000002"),
                                        name = "Alex's MacBook Pro",
                                        platformName = "macOS",
                                        lastActiveFormatted = "Yesterday",
                                        appVersion = "1.0.0",
                                        isCurrentDevice = false,
                                    ),
                                ),
                        ),
                )
            },
            sharedScene(SharedScreenshotSceneId.StreakSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                StreakSettingsContent(
                    streakData = StreakData(currentStreak = 12, isEnabled = true),
                    onBack = {},
                    onToggleStreakTracking = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.TimelineSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                TimelineSettingsContent(
                    onBack = {},
                    onNavigateToDayBoundary = {},
                    sleepBasedBoundariesEnabled = true,
                    healthConnectStatus = HealthConnectStatus.CONNECTED,
                )
            },
            sharedScene(SharedScreenshotSceneId.DayBoundarySettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                DayBoundarySettingsContent(
                    sleepBasedPreferenceEnabled = true,
                    fallbackStartHour = 4,
                    gateState = HealthConnectGateState(kind = HealthConnectGateKind.READY),
                    isRequestInFlight = false,
                    onBack = {},
                    onToggleSleepBased = {},
                    onSetFallbackHour = {},
                    onRequestPermissions = {},
                    onSetUpHealthConnect = {},
                    onOpenHealthConnectPermissions = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.LibrarySettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                LibrarySettingsContent(
                    onBack = {},
                    isLibraryEnabled = true,
                    onSetLibraryEnabled = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.RecommendationSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                RecommendationSettingsContent(
                    settings =
                        MemoriesSettings(
                            contextualRecommendationsEnabled = true,
                            ambientPromptsEnabled = true,
                            morningPromptTime = AmbientPromptTime(hour = 8, minute = 30),
                            eveningPromptTime = AmbientPromptTime(hour = 21, minute = 0),
                            aiRecallEnabled = true,
                        ),
                    onBack = {},
                    onToggleContextualRecommendations = {},
                    onToggleAmbientPrompts = {},
                    onToggleCaptureNudges = {},
                    onToggleDraftRescue = {},
                    onToggleMemoryRecallNotifications = {},
                    onToggleMorningPrompt = {},
                    onToggleEveningPrompt = {},
                    onSetMorningPromptTime = {},
                    onSetEveningPromptTime = {},
                    onToggleSmartRecall = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.BirthdaySettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                BirthdaySettingsContent(
                    currentBirthday = baseInstant,
                    onBack = {},
                    onSave = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.AdvancedSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                AdvancedSettingsContent(
                    onBack = {},
                    appUpdateUiState =
                        AppUpdateUiState(
                            currentVersionName = "1.0.0",
                            currentVersionCode = 100,
                            status = AppUpdateStatus.UpToDate,
                            lastCheckedAt = baseInstant,
                        ),
                    onCheckForAppUpdates = {},
                    onCompleteAppUpdate = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.WatchSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                WatchSettingsContent(
                    connectionState =
                        WatchConnectionState.Connected(
                            watchName = "Pixel Watch 3",
                            lastSynced = baseInstant,
                            pendingCount = 2,
                        ),
                    onBack = {},
                    onBeginAssociation = {},
                    onRequestSync = {},
                    onInstallOnWatch = {},
                    onNavigateToSync = {},
                    onNavigateToNotifications = {},
                    onNavigateToTroubleshooting = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.WatchSyncSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                WatchSyncSettingsContent(
                    settings =
                        WatchSyncSettings(
                            syncVoiceNotes = true,
                            syncTextEntries = true,
                            syncMoodCheckIns = true,
                            syncHealthData = false,
                            autoSync = true,
                        ),
                    onBack = {},
                    onSetSyncVoiceNotes = {},
                    onSetSyncTextEntries = {},
                    onSetSyncMoodCheckIns = {},
                    onSetSyncHealthData = {},
                    onSetAutoSync = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.WatchNotificationSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                WatchNotificationSettingsContent(
                    settings =
                        WatchNotificationSettings(
                            showEntryNotifications = true,
                            includeAudioPreview = false,
                        ),
                    onBack = {},
                    onSetShowEntryNotifications = {},
                    onSetIncludeAudioPreview = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.WatchTroubleshooting, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
                WatchTroubleshootingContent(
                    connectionState =
                        WatchConnectionState.AppNotInstalled(
                            watchName = "Pixel Watch 3",
                        ),
                    onBack = {},
                    onBeginAssociation = {},
                    onInstallOnWatch = {},
                    onOpenOnWatch = {},
                )
            },
        )

    val searchScenes: List<SharedScreenshotSceneSpec> =
        listOf(
            sharedScene(SharedScreenshotSceneId.SearchIdle, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
                SearchScreenContent(
                    searchState =
                        SearchScreenState.Idle(
                            recentSearches =
                                listOf(
                                    "sunrise trail",
                                    "voice memo",
                                    "budget review",
                                ),
                        ),
                    queryText = "",
                    initialQuery = "",
                    onQueryChange = {},
                    onCommitSearch = {},
                    onResultClick = {},
                    onResultOpenDay = {},
                    onGoBack = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.SearchSearching, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
                SearchScreenContent(
                    searchState = SearchScreenState.Searching(query = SEARCH_QUERY),
                    queryText = SEARCH_QUERY,
                    initialQuery = SEARCH_QUERY,
                    onQueryChange = {},
                    onCommitSearch = {},
                    onResultClick = {},
                    onResultOpenDay = {},
                    onGoBack = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.SearchEmpty, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
                SearchScreenContent(
                    searchState = SearchScreenState.Empty(query = SEARCH_QUERY),
                    queryText = SEARCH_QUERY,
                    initialQuery = SEARCH_QUERY,
                    onQueryChange = {},
                    onCommitSearch = {},
                    onResultClick = {},
                    onResultOpenDay = {},
                    onGoBack = {},
                )
            },
            sharedScene(SharedScreenshotSceneId.SearchResults, ScreenshotSceneGroup.SEARCH, standardMatrixVariants) {
                SearchScreenContent(
                    searchState = SearchScreenState.Results(query = SEARCH_QUERY, results = searchResults),
                    queryText = SEARCH_QUERY,
                    initialQuery = SEARCH_QUERY,
                    onQueryChange = {},
                    onCommitSearch = {},
                    onResultClick = {},
                    onResultOpenDay = {},
                    onGoBack = {},
                )
            },
        )

    val desktopOnlyScenes: List<SharedScreenshotSceneSpec> =
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
                    message = "Whoops, we couldn't catch the rewind. Try again later.",
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

    val allScenes: List<SharedScreenshotSceneSpec> =
        onboardingMainScenes + onboardingExtendedScenes + settingsScenes + searchScenes + desktopOnlyScenes

    fun scene(id: SharedScreenshotSceneId): SharedScreenshotSceneSpec = allScenes.first { it.id == id }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun SharedScreenshotScene(sceneId: SharedScreenshotSceneId) {
    SharedScreenshotCatalog.scene(sceneId).content()
}

fun screenshotBaselineName(
    scene: SharedScreenshotSceneSpec,
    variant: ScreenshotSceneVariant,
): String =
    variant.viewport.baselineSuffix.takeIf { it.isNotBlank() }?.let { suffix ->
        "${scene.id.value}--$suffix"
    } ?: scene.id.value

private fun sharedScene(
    id: SharedScreenshotSceneId,
    group: ScreenshotSceneGroup,
    variants: List<ScreenshotSceneVariant>,
    content: @Composable () -> Unit,
): SharedScreenshotSceneSpec =
    SharedScreenshotSceneSpec(
        id = id,
        group = group,
        variants = variants,
        content = content,
    )

private const val SEARCH_QUERY = "sun"

private val baseInstant = Instant.fromEpochMilliseconds(1_740_000_000_000L)

private val rewindPreview =
    RewindPreviewUiState(
        message = "You packed this week with movement, memories, and a lot of camera roll receipts.",
        rewindId = Uuid.parse("11111111-2222-3333-4444-555555555555"),
        label = "This Week",
        title = "Five cities in seven days",
        start = LocalDate(2026, 4, 6),
        end = LocalDate(2026, 4, 12),
        rewindAvailable = true,
        isViewed = false,
        entryCount = 9,
        photoCount = 41,
        peopleCount = 6,
        primaryLocation = "San Francisco",
    )

private val pastRewindSamples =
    listOf(
        RewindHistoryUiState(
            uid = Uuid.parse("22222222-3333-4444-5555-666666666666"),
            title = "A quieter week at home",
            label = "Week of Mar 30",
            startDate = LocalDate(2026, 3, 30),
            endDate = LocalDate(2026, 4, 5),
            message = "Rain three days running, three book chapters, and one really good chili.",
            isViewed = true,
            entryCount = 6,
            photoCount = 12,
            peopleCount = 2,
            primaryLocation = "Brooklyn",
        ),
        RewindHistoryUiState(
            uid = Uuid.parse("33333333-4444-5555-6666-777777777777"),
            title = "The trip that almost wasn't",
            label = "Week of Mar 23",
            startDate = LocalDate(2026, 3, 23),
            endDate = LocalDate(2026, 3, 29),
            message = "Late flight, new city, and the kind of dinner you talk about for months.",
            isViewed = true,
            entryCount = 11,
            photoCount = 53,
            peopleCount = 4,
            primaryLocation = "Lisbon",
        ),
        RewindHistoryUiState(
            uid = Uuid.parse("44444444-5555-6666-7777-888888888888"),
            title = "Heads-down week",
            label = "Week of Mar 16",
            startDate = LocalDate(2026, 3, 16),
            endDate = LocalDate(2026, 3, 22),
            message = "You shipped the thing on Friday and slept eleven hours on Saturday.",
            isViewed = true,
            entryCount = 4,
            photoCount = 7,
            peopleCount = 1,
            primaryLocation = "Brooklyn",
        ),
    )

private val rewindDetailPanels =
    listOf(
        SubtitledRewindPanelUiState(
            title = "Five cities in seven days",
            subtitle = "April 6 — April 12, 2026",
        ),
        NarrativeContextRewindPanelUiState(
            sourceId = Uuid.parse("aaaaaaaa-1111-2222-3333-444444444444"),
            timestamp = baseInstant,
            contextText = "You started in San Francisco and chased one open afternoon at a time across the coast.",
        ),
        HighlightedQuoteRewindPanelUiState(
            text = "I forgot how loud the ocean is when nothing else is.",
            whyItHits = "You came back to silence twice this week.",
        ),
        BasicTextRewindPanelUiState(
            text = "That was your week.",
        ),
    )

private val searchResults =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000061"),
            content = "Caught the sunrise train and wrote down the quiet before the city woke up.",
            created = baseInstant,
            contentType = SearchContentType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000062"),
            content = "Voice memo on the sunrise trail, the route changes, and what still needs shipping.",
            created = baseInstant,
            contentType = SearchContentType.TRANSCRIPTION,
        ),
    )

private fun sampleMemories(
    videoEvery: Int = 3,
    videoDuration: Duration = Duration.parse("30s"),
): List<MediaObject> =
    (1..12).map { index ->
        val timestamp = Instant.fromEpochMilliseconds(baseInstant.toEpochMilliseconds() + (index * 60_000))
        if (index % videoEvery == 0) {
            MediaObject.Video(
                uri = "sample$index",
                size = 2048,
                name = "VID_$index.mp4",
                timestamp = timestamp,
                duration = videoDuration,
            )
        } else {
            MediaObject.Image(
                uri = "sample$index",
                size = 1024,
                name = "IMG_$index.jpg",
                timestamp = timestamp,
            )
        }
    }
