package app.logdate.screenshots.flows.flow07_settings_account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.AccountIdentityState
import app.logdate.feature.core.settings.ui.AccountSettingsContent
import app.logdate.feature.core.settings.ui.AdvancedSettingsContent
import app.logdate.feature.core.settings.ui.BirthdayUpdateState
import app.logdate.feature.core.settings.ui.ConflictsState
import app.logdate.feature.core.settings.ui.DangerZoneSettingsContent
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.LocationSettingsContent
import app.logdate.feature.core.settings.ui.PasskeyCreationState
import app.logdate.feature.core.settings.ui.PasskeyInfo
import app.logdate.feature.core.settings.ui.PasskeyRevocationState
import app.logdate.feature.core.settings.ui.PrivacySettingsContent
import app.logdate.feature.core.settings.ui.ProfileUpdateState
import app.logdate.feature.core.settings.ui.RestoreState
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.feature.core.settings.ui.ServerValidationState
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.feature.core.settings.updates.AppUpdateFlowType
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.navigation.scenes.SettingsEmptyDetailPane
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerPasskeyConfig
import app.logdate.shared.model.user.UserData
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant

private val sampleUserProfile =
    UserProfile(
        name = "Alex Johnson",
        username = "alex_j",
        isEditable = true,
        isAuthenticated = true,
    )

private val signedOutUserProfile =
    UserProfile(
        name = "Local User",
        username = "",
        isEditable = true,
        isAuthenticated = false,
    )

private val sampleQuota =
    StorageQuotaUi(
        totalBytes = 5_368_709_120L,
        usedBytes = 2_147_483_648L,
        usagePercentage = 0.4f,
        formattedTotal = "5.0 GB",
        formattedUsed = "2.0 GB",
    )

private val samplePasskeys =
    listOf(
        PasskeyInfo(
            id = "credential-1",
            name = "Pixel 9 Pro",
            device = "Pixel 9 Pro",
            createdAt = "Feb 2025",
            lastUsed = ScreenshotTestData.baseInstant,
        ),
        PasskeyInfo(
            id = "credential-2",
            name = "MacBook Air",
            device = "MacBook Air",
            createdAt = "Jan 2025",
            lastUsed = ScreenshotTestData.baseInstant,
        ),
    )

private val sampleIdentityState =
    AccountIdentityState(
        status =
            AccountIdentityStatus(
                did = "did:plc:preview123",
                handle = "alex_j.logdate.app",
                signingKeyPublicMultibase = "zPreview",
                signingKeyDidKey = "did:key:zPreview",
                plcRecoveryDidKey = "did:key:zRecovery",
                plcOperationCount = 2,
            ),
    )

private val sampleServerDescriptor =
    ServerDescriptor(
        serverOrigin = "https://logdate.app",
        apiBaseUrl = "https://logdate.app/api",
        deploymentKind = DeploymentKind.FIRST_PARTY,
        displayName = "LogDate Cloud",
        handleDomain = "logdate.app",
        passkey = ServerPasskeyConfig(rpId = "logdate.app", rpName = "LogDate"),
        capabilities = listOf(ServerCapability.AUTH_PASSKEY, ServerCapability.MANAGED_QUOTA),
    )

private val sampleSyncStatus =
    SyncStatus(
        isEnabled = true,
        lastSyncTime = ScreenshotTestData.baseInstant,
        pendingUploads = 0,
        isSyncing = false,
        hasErrors = false,
    )

private val sampleConflicts =
    listOf(
        SyncConflictRecord(
            id = "conflict-1",
            entityType = "note",
            entityId = "note-1",
            localVersion = 4,
            remoteVersion = 5,
            localUpdatedAt = 1_740_000_000_000L,
            remoteUpdatedAt = 1_740_003_600_000L,
            reason = "Both devices edited the same note before sync completed.",
            detectedAt = 1_740_007_200_000L,
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_SettingsOverviewDefault() {
    ScreenshotTheme {
        SettingsOverviewContent(
            onBack = {},
            onNavigateToProfile = {},
            onNavigateToAccount = {},
            onNavigateToPrivacy = {},
            onNavigateToData = {},
            onNavigateToDevices = {},
            onNavigateToDangerZone = {},
            onNavigateToLocation = {},
            onNavigateToAdvanced = {},
            userProfile = sampleUserProfile,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_SettingsOverviewEmptyDetail() {
    ScreenshotTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToPrivacy = {},
                onNavigateToData = {},
                onNavigateToDevices = {},
                onNavigateToDangerZone = {},
                onNavigateToLocation = {},
                onNavigateToAdvanced = {},
                userProfile = sampleUserProfile,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SettingsEmptyDetailPane()
            }
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_AccountSettingsAuthenticated() {
    ScreenshotTheme {
        AccountSettingsContent(
            onBack = {},
            onCreatePasskey = {},
            onUpdateBirthday = {},
            onResetBirthdayUpdateState = {},
            userProfile = sampleUserProfile,
            passkeys = samplePasskeys,
            userData = UserData(),
            isAuthenticated = true,
            onUpdateProfile = { _, _ -> },
            onRevokePasskey = {},
            onSignOut = { _ -> },
            birthdayUpdateState = BirthdayUpdateState.Idle,
            profileUpdateState = ProfileUpdateState.Idle,
            identityState = sampleIdentityState,
            onRefreshIdentity = {},
            onExportSigningKey = {},
            onRotateSigningKey = {},
            onImportSigningKey = { _, _ -> },
            onRegisterPlcRecoveryKey = {},
            onClearIdentityActionState = {},
            onClearExportedKeyJson = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_AccountSettingsSignedOut() {
    ScreenshotTheme {
        AccountSettingsContent(
            onBack = {},
            onCreatePasskey = {},
            onUpdateBirthday = {},
            onResetBirthdayUpdateState = {},
            userProfile = signedOutUserProfile,
            passkeys = emptyList(),
            userData = UserData(),
            isAuthenticated = false,
            onUpdateProfile = { _, _ -> },
            onRevokePasskey = {},
            onSignOut = { _ -> },
            birthdayUpdateState = BirthdayUpdateState.Idle,
            profileUpdateState = ProfileUpdateState.Idle,
            identityState = AccountIdentityState(),
            onRefreshIdentity = {},
            onExportSigningKey = {},
            onRotateSigningKey = {},
            onImportSigningKey = { _, _ -> },
            onRegisterPlcRecoveryKey = {},
            onClearIdentityActionState = {},
            onClearExportedKeyJson = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_PrivacySettingsDefault() {
    ScreenshotTheme {
        PrivacySettingsContent(
            onBack = {},
            onSetBiometricsEnabled = {},
            isBiometricsEnabled = false,
            isAuthenticated = false,
            onNavigateToLocationSettings = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_PrivacySettingsWithPasskeys() {
    ScreenshotTheme {
        PrivacySettingsContent(
            onBack = {},
            onSetBiometricsEnabled = {},
            isBiometricsEnabled = true,
            isAuthenticated = true,
            passkeys = samplePasskeys,
            onCreatePasskey = {},
            onRevokePasskey = {},
            onNavigateToLocationSettings = {},
            revocationState = PasskeyRevocationState.Idle,
            creationState = PasskeyCreationState.Idle,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_DataSettingsSignedOut() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DataSettingsContent(
            onBack = {},
            quotaUsage = sampleQuota,
            isQuotaAvailable = false,
            exportState = app.logdate.feature.core.export.ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onShareExport = {},
            onRestoreContent = {},
            onCancelRestore = {},
            restoreState = RestoreState.Idle,
            integrityState = IntegrityState(),
            onRunIntegrityCheck = {},
            onRepairIntegrity = {},
            conflictsState = ConflictsState(),
            onClearConflicts = {},
            onRefreshConflicts = {},
            snackbarHostState = snackbarHostState,
            syncStatus = null,
            isAuthenticated = false,
            onSyncNow = {},
            isBackgroundSyncEnabled = false,
            onBackgroundSyncEnabledChange = {},
            onNavigateToSignIn = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_DataSettingsSignedIn() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DataSettingsContent(
            onBack = {},
            quotaUsage = sampleQuota,
            isQuotaAvailable = true,
            exportState = app.logdate.feature.core.export.ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onShareExport = {},
            onRestoreContent = {},
            onCancelRestore = {},
            restoreState = RestoreState.Idle,
            integrityState = IntegrityState(),
            onRunIntegrityCheck = {},
            onRepairIntegrity = {},
            conflictsState = ConflictsState(),
            onClearConflicts = {},
            onRefreshConflicts = {},
            snackbarHostState = snackbarHostState,
            syncStatus = sampleSyncStatus,
            isAuthenticated = true,
            onSyncNow = {},
            isBackgroundSyncEnabled = true,
            onBackgroundSyncEnabledChange = {},
            onNavigateToSignIn = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_DataSettingsRestoreCompleted() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DataSettingsContent(
            onBack = {},
            quotaUsage = sampleQuota,
            isQuotaAvailable = true,
            exportState = app.logdate.feature.core.export.ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onShareExport = {},
            onRestoreContent = {},
            onCancelRestore = {},
            restoreState =
                RestoreState.Completed(
                    summary =
                        RestoreSummary(
                            source = "preview",
                            exportDate = ScreenshotTestData.baseInstant,
                            journalsImported = 3,
                            notesImported = 18,
                            draftsImported = 0,
                            journalLinksImported = 18,
                            mediaImported = 4,
                        ),
                    showSnackbar = false,
                ),
            integrityState = IntegrityState(),
            onRunIntegrityCheck = {},
            onRepairIntegrity = {},
            conflictsState = ConflictsState(),
            onClearConflicts = {},
            onRefreshConflicts = {},
            snackbarHostState = snackbarHostState,
            syncStatus = sampleSyncStatus,
            isAuthenticated = true,
            onSyncNow = {},
            isBackgroundSyncEnabled = true,
            onBackgroundSyncEnabledChange = {},
            onNavigateToSignIn = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_DataSettingsConflicts() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DataSettingsContent(
            onBack = {},
            quotaUsage = sampleQuota,
            isQuotaAvailable = true,
            exportState = app.logdate.feature.core.export.ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onShareExport = {},
            onRestoreContent = {},
            onCancelRestore = {},
            restoreState = RestoreState.Idle,
            integrityState = IntegrityState(),
            onRunIntegrityCheck = {},
            onRepairIntegrity = {},
            conflictsState =
                ConflictsState(
                    conflicts = sampleConflicts,
                    isLoading = false,
                    lastUpdated = Instant.fromEpochMilliseconds(1_740_007_200_000L),
                ),
            onClearConflicts = {},
            onRefreshConflicts = {},
            snackbarHostState = snackbarHostState,
            syncStatus = sampleSyncStatus,
            isAuthenticated = true,
            onSyncNow = {},
            isBackgroundSyncEnabled = true,
            onBackgroundSyncEnabledChange = {},
            onNavigateToSignIn = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S11_DangerZoneSettingsDefault() {
    ScreenshotTheme {
        DangerZoneSettingsContent(
            onBack = {},
            onAppReset = {},
            onClearData = { _, _ -> },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S12_LocationSettingsTrackingOff() {
    ScreenshotTheme {
        LocationSettingsContent(
            settings = LocationTrackingSettings(backgroundTrackingEnabled = false),
            onBack = {},
            onToggleBackgroundTracking = {},
            onToggleJournalTracking = {},
            onToggleTimelineTracking = {},
            onUpdateTrackingInterval = {},
            onShowLocationTimeline = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S13_LocationSettingsTrackingOn() {
    ScreenshotTheme {
        LocationSettingsContent(
            settings =
                LocationTrackingSettings(
                    backgroundTrackingEnabled = true,
                    trackingIntervalMinutes = 45,
                    autoTrackForJournalEntries = true,
                    autoTrackForTimelineReview = true,
                ),
            onBack = {},
            onToggleBackgroundTracking = {},
            onToggleJournalTracking = {},
            onToggleTimelineTracking = {},
            onUpdateTrackingInterval = {},
            onShowLocationTimeline = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S14_AdvancedSettingsDefault() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            serverSelectionState =
                ServerSelectionState(
                    selectedPreset = ServerPreset.PRODUCTION,
                    activeServerDescriptor = sampleServerDescriptor,
                ),
            appUpdateUiState = AppUpdateUiState(currentVersionName = "0.1.0"),
            onSelectPreset = {},
            onUpdateCustomUrl = {},
            onValidateAndSave = {},
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
            onShowCustomServerInfo = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S15_AdvancedSettingsUpdateReady() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            serverSelectionState =
                ServerSelectionState(
                    selectedPreset = ServerPreset.PRODUCTION,
                    activeServerDescriptor = sampleServerDescriptor,
                ),
            appUpdateUiState =
                AppUpdateUiState(
                    currentVersionName = "0.1.0",
                    status = AppUpdateStatus.Downloaded,
                ),
            onSelectPreset = {},
            onUpdateCustomUrl = {},
            onValidateAndSave = {},
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
            onShowCustomServerInfo = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S16_AdvancedSettingsCustomServerError() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            serverSelectionState =
                ServerSelectionState(
                    selectedPreset = ServerPreset.CUSTOM,
                    customServerUrl = "https://preview.logdate.dev",
                    validationState = ServerValidationState.Error("Unable to reach server"),
                    activeServerDescriptor = sampleServerDescriptor,
                ),
            appUpdateUiState =
                AppUpdateUiState(
                    currentVersionName = "0.1.0",
                    status = AppUpdateStatus.Available,
                    flowType = AppUpdateFlowType.Immediate,
                ),
            onSelectPreset = {},
            onUpdateCustomUrl = {},
            onValidateAndSave = {},
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
            onShowCustomServerInfo = {},
        )
    }
}
