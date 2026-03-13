package app.logdate.screenshots.components.settings_account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.settings.ui.AccountIdentityState
import app.logdate.feature.core.settings.ui.AccountSettingsContent
import app.logdate.feature.core.settings.ui.AdvancedSettingsContent
import app.logdate.feature.core.settings.ui.ConflictsState
import app.logdate.feature.core.settings.ui.DangerZoneSettingsContent
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.LocationSettingsContent
import app.logdate.feature.core.settings.ui.PrivacySettingsContent
import app.logdate.feature.core.settings.ui.RestoreState
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.feature.core.settings.ui.dialogs.DangerConfirmationDialog
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.feature.core.settings.updates.AppUpdateFlowType
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTestData.TABLET
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private val sampleUserProfile = UserProfile(
    name = "Alex Johnson",
    username = "alex_j",
    isEditable = true,
    isAuthenticated = true,
)

private val sampleQuota = StorageQuotaUi(
    totalBytes = 5_368_709_120L, // 5 GB
    usedBytes = 2_147_483_648L, // 2 GB
    usagePercentage = 0.4f,
    formattedTotal = "5.0 GB",
    formattedUsed = "2.0 GB",
)

private val sampleIdentityState = AccountIdentityState(
    status = AccountIdentityStatus(
        did = "did:plc:preview123",
        handle = "alex_j.logdate.app",
        signingKeyPublicMultibase = "zPreview",
        signingKeyDidKey = "did:key:zPreview",
        plcRecoveryDidKey = "did:key:zRecovery",
        plcOperationCount = 2,
    ),
)

// ─── Settings Overview ──────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun SettingsOverview() {
    ScreenshotTheme {
        SettingsOverviewContent(
            onBack = {},
            onNavigateToProfile = {},
            onNavigateToAccount = {},
            onNavigateToDevices = {},
            onNavigateToDangerZone = {},
            onNavigateToLocation = {},
            onNavigateToMemories = {},
            onNavigateToSync = {},
            onNavigateToExport = {},
            userProfile = sampleUserProfile,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsOverview_Dark() {
    ScreenshotTheme(darkTheme = true) {
        SettingsOverviewContent(
            onBack = {},
            onNavigateToProfile = {},
            onNavigateToAccount = {},
            onNavigateToDevices = {},
            onNavigateToDangerZone = {},
            onNavigateToLocation = {},
            onNavigateToMemories = {},
            onNavigateToSync = {},
            onNavigateToExport = {},
            userProfile = sampleUserProfile,
        )
    }
}

// ─── Account Settings ───────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AccountSettings_Default() {
    ScreenshotTheme {
        AccountSettingsContent(
            onBack = {},
            onCreatePasskey = {},
            userProfile = sampleUserProfile,
            passkeys = emptyList(),
            isAuthenticated = true,
            onRevokePasskey = {},
            onSignOut = { _ -> },
            identityState = sampleIdentityState,
            onRefreshIdentity = {},
            onExportSigningKey = {},
            onRotateSigningKey = {},
            onImportSigningKey = { _, _ -> },
            onImportSigningKeyWithRecovery = { _, _, _ -> },
            onDerivePlcRecoveryKey = {},
            onRegisterPlcRecoveryKey = {},
            onRegisterDerivedPlcRecoveryKey = {},
            onClearIdentityActionState = {},
            onClearDerivedRecoveryDidKey = {},
            onClearExportedKeyJson = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AccountSettings_NoAccount() {
    ScreenshotTheme {
        AccountSettingsContent(
            onBack = {},
            onCreatePasskey = {},
            userProfile = UserProfile(name = "Local User", username = "", isAuthenticated = false),
            passkeys = emptyList(),
            isAuthenticated = false,
            onRevokePasskey = {},
            onSignOut = { _ -> },
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
            onClearDerivedRecoveryDidKey = {},
            onClearExportedKeyJson = {},
        )
    }
}

// ─── Privacy Settings ───────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun PrivacySettings() {
    ScreenshotTheme {
        PrivacySettingsContent(
            onBack = {},
            onSetBiometricsEnabled = {},
            isBiometricsEnabled = false,
            isAuthenticated = true,
        )
    }
}

// ─── Data Settings ──────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DataSettings() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DataSettingsContent(
            onBack = {},
            quotaUsage = sampleQuota,
            isQuotaAvailable = true,
            exportState = ExportState.Idle,
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
        )
    }
}

// ─── Location Settings ──────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationSettings() {
    ScreenshotTheme {
        LocationSettingsContent(
            settings = LocationTrackingSettings(),
            onBack = {},
            onToggleBackgroundTracking = {},
            onShowLocationTimeline = {},
            onNavigateToTrackingOptions = {},
            onNavigateToInterval = {},
            onNavigateToAdvanced = {},
        )
    }
}

// ─── Advanced Settings ──────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AdvancedSettings() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            appUpdateUiState = AppUpdateUiState(currentVersionName = "0.1.0"),
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
        )
    }
}

/** Captures the advanced settings state after a flexible update has downloaded. */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AdvancedSettings_UpdateReady() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            appUpdateUiState =
                AppUpdateUiState(
                    currentVersionName = "0.1.0",
                    status = AppUpdateStatus.Downloaded,
                ),
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
        )
    }
}

/** Captures the advanced settings state when an immediate Play update is available. */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AdvancedSettings_UpdateAvailableImmediate() {
    ScreenshotTheme {
        AdvancedSettingsContent(
            onBack = {},
            appUpdateUiState =
                AppUpdateUiState(
                    currentVersionName = "0.1.0",
                    status = AppUpdateStatus.Available,
                    flowType = AppUpdateFlowType.Immediate,
                ),
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
        )
    }
}

// ─── Danger Zone ────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DangerZoneSettings() {
    ScreenshotTheme {
        DangerZoneSettingsContent(
            onBack = {},
            onAppReset = {},
            onClearData = { _, _ -> },
        )
    }
}

// ─── Dialogs ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun ResetAppConfirmation_Dialog() {
    ScreenshotTheme {
        ResetAppConfirmationDialog(
            onDismissRequest = {},
            onConfirmation = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DangerConfirmation_Dialog() {
    ScreenshotTheme {
        DangerConfirmationDialog(
            onDismissRequest = {},
            onConfirmation = {},
            title = "Delete All Data",
            message = "This action cannot be undone. All your journals, notes, and media will be permanently deleted.",
            confirmButtonText = "Delete Everything",
        )
    }
}

// ─── List-Detail (Landscape / Tablet) ───────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE_LANDSCAPE)
@Composable
fun SettingsListDetail_Landscape_Account() {
    ScreenshotTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToDevices = {},
                onNavigateToDangerZone = {},
                onNavigateToLocation = {},
                onNavigateToMemories = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = sampleUserProfile,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AccountSettingsContent(
                    onBack = {},
                    onCreatePasskey = {},
                    userProfile = sampleUserProfile,
                    passkeys = emptyList(),
                    isAuthenticated = true,
                    onRevokePasskey = {},
                    onSignOut = { _ -> },
                    identityState = sampleIdentityState,
                    onRefreshIdentity = {},
                    onExportSigningKey = {},
                    onRotateSigningKey = {},
                    onImportSigningKey = { _, _ -> },
                    onImportSigningKeyWithRecovery = { _, _, _ -> },
                    onDerivePlcRecoveryKey = {},
                    onRegisterPlcRecoveryKey = {},
                    onRegisterDerivedPlcRecoveryKey = {},
                    onClearIdentityActionState = {},
                    onClearDerivedRecoveryDidKey = {},
                    onClearExportedKeyJson = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = TABLET)
@Composable
fun SettingsListDetail_Tablet_Account() {
    ScreenshotTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToDevices = {},
                onNavigateToDangerZone = {},
                onNavigateToLocation = {},
                onNavigateToMemories = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = sampleUserProfile,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AccountSettingsContent(
                    onBack = {},
                    onCreatePasskey = {},
                    userProfile = sampleUserProfile,
                    passkeys = emptyList(),
                    isAuthenticated = true,
                    onRevokePasskey = {},
                    onSignOut = { _ -> },
                    identityState = sampleIdentityState,
                    onRefreshIdentity = {},
                    onExportSigningKey = {},
                    onRotateSigningKey = {},
                    onImportSigningKey = { _, _ -> },
                    onImportSigningKeyWithRecovery = { _, _, _ -> },
                    onDerivePlcRecoveryKey = {},
                    onRegisterPlcRecoveryKey = {},
                    onRegisterDerivedPlcRecoveryKey = {},
                    onClearIdentityActionState = {},
                    onClearDerivedRecoveryDidKey = {},
                    onClearExportedKeyJson = {},
                )
            }
        }
    }
}
