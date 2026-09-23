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
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.settings.ui.AdvancedSettingsContent
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.LocationSettingsContent
import app.logdate.feature.core.settings.ui.PrivacySettingsContent
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.feature.core.settings.ui.dialogs.DangerConfirmationDialog
import app.logdate.feature.core.settings.ui.dialogs.ClearDataConfirmationDialog
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.feature.core.settings.updates.AppUpdateFlowType
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTestData.TABLET
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.feature.core.settings.account.AccountContent
import app.logdate.feature.core.settings.account.AccountDestinations
import app.logdate.feature.core.settings.account.AccountHeader
import app.logdate.feature.core.settings.account.AccountUiState
import app.logdate.feature.core.settings.account.EmailRow
import app.logdate.feature.core.settings.account.ServerRow
import app.logdate.feature.core.settings.account.SignInSummary
import androidx.compose.runtime.CompositionLocalProvider
import app.logdate.ui.common.formatting.LocalToday
import kotlinx.datetime.LocalDate
import app.logdate.feature.core.settings.account.signin.SignInMethodsContent
import app.logdate.feature.core.settings.account.signin.SignInMethodsUiState
import app.logdate.feature.core.settings.account.signin.PasskeyRow
import app.logdate.feature.core.settings.account.signin.LinkedProviderRow
import app.logdate.feature.core.settings.account.recovery.RecoveryPhraseContent
import app.logdate.feature.core.settings.account.recovery.RecoveryPhraseUiState
import app.logdate.feature.core.settings.account.hosting.HostingContent
import app.logdate.feature.core.settings.account.hosting.HostingUiState
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import app.logdate.feature.core.settings.account.delete.DeleteAccountContent
import app.logdate.feature.core.settings.account.delete.DeleteAccountUiState
import app.logdate.feature.core.settings.account.move.AccountProblem
import app.logdate.feature.core.settings.account.move.ChooseProblem
import app.logdate.feature.core.settings.account.move.Cleanup
import app.logdate.feature.core.settings.account.move.MoveEndpoint
import app.logdate.feature.core.settings.account.move.MoveProgress
import app.logdate.feature.core.settings.account.move.MoveServerContent
import app.logdate.feature.core.settings.account.move.MoveServerUiState
import app.logdate.feature.core.settings.account.move.MoveSurvey
import app.logdate.feature.core.settings.account.move.ServerMoveRecord
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.feature.core.settings.ui.ServerProblem
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor

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
            onNavigateToReset = {},
            onNavigateToLocation = {},
            onNavigateToPrivacy = {},
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
            onNavigateToReset = {},
            onNavigateToLocation = {},
            onNavigateToPrivacy = {},
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
        AccountContent(
            state =
                AccountUiState.SignedIn(
                    header = AccountHeader(displayName = "Alex Rivera", username = "alex"),
                    signIn = SignInSummary.Known(passkeyCount = 2, linkedProviders = listOf(LinkedSignInProvider.Kind.GOOGLE)),
                    hasRecoveryPhrase = true,
                    email = EmailRow(address = "alex@example.com", isVerified = true, canVerify = false),
                    server = ServerRow(name = "LogDate Cloud", host = "cloud.logdate.app", isLogDateCloud = true),
                    isSigningOut = false,
                ),
            destinations = AccountDestinations({}, {}, {}, {}, {}, {}, {}, {}),
            onOpenEmailVerification = {},
            onSignOut = {},
        )
    }
}

// ─── Account subpages ───────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun SignInMethods() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalToday provides LocalDate(2026, 9, 23)) {
            SignInMethodsContent(
                state =
                    SignInMethodsUiState.Loaded(
                        passkeys =
                            listOf(
                                PasskeyRow("passkey-pixel", "Pixel 9", LocalDate(2026, 3, 12), LocalDate(2026, 9, 23), canRemove = true),
                                PasskeyRow("passkey-mac", "MacBook Pro", LocalDate(2026, 9, 22), null, canRemove = true),
                                PasskeyRow("passkey-old", null, LocalDate(2025, 11, 2), LocalDate(2026, 1, 4), canRemove = true),
                            ),
                        linkedProviders =
                            listOf(LinkedProviderRow(LinkedSignInProvider.Kind.GOOGLE, "alex@example.com", LocalDate(2026, 9, 1))),
                        canAddPasskey = true,
                    ),
                onBack = {},
                onRetry = {},
                onAddPasskey = {},
                onRemovePasskey = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun SignInMethods_OnlyMethod() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalToday provides LocalDate(2026, 9, 23)) {
            SignInMethodsContent(
                state =
                    SignInMethodsUiState.Loaded(
                        passkeys = listOf(PasskeyRow("passkey-pixel", "Pixel 9", LocalDate(2026, 9, 23), null, canRemove = false)),
                        linkedProviders = emptyList(),
                        canAddPasskey = true,
                    ),
                onBack = {},
                onRetry = {},
                onAddPasskey = {},
                onRemovePasskey = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryPhrase_Revealed() {
    ScreenshotTheme {
        RecoveryPhraseContent(
            state =
                RecoveryPhraseUiState.Revealed(
                    listOf("orbit", "canvas", "meadow", "lantern", "harbor", "velvet", "summit", "pepper", "gravel", "whisper", "copper", "tundra"),
                ),
            onBack = {},
            onReveal = {},
            onHide = {},
            onEnterPhrase = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryPhrase_NotOnThisDevice() {
    ScreenshotTheme {
        RecoveryPhraseContent(
            state = RecoveryPhraseUiState.NotOnThisDevice,
            onBack = {},
            onReveal = {},
            onHide = {},
            onEnterPhrase = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Hosting() {
    ScreenshotTheme {
        HostingContent(
            state =
                HostingUiState(
                    server =
                        ConnectedServerInfo(
                            origin = "https://cloud.logdate.app",
                            displayName = "LogDate Cloud",
                            isLogDateCloud = true,
                            publishesIdentityChanges = false,
                        ),
                    health = ServerHealth.Reachable("1.4.0"),
                ),
            onBack = {},
            onCheckAgain = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DeleteAccount() {
    ScreenshotTheme {
        DeleteAccountContent(
            state = DeleteAccountUiState(serverName = "LogDate Cloud"),
            onBack = {},
            onEraseThisDeviceChange = {},
            onDelete = {},
        )
    }
}

// ─── Move to another server ─────────────────────────────────────────────────────

private val moveSource = MoveEndpoint("https://cloud.logdate.app", null)
private val moveDestination =
    CheckedServer(
        origin = "https://journal.example.com",
        descriptor =
            ServerDescriptor(
                serverOrigin = "https://journal.example.com",
                apiBaseUrl = "https://journal.example.com/api/v1",
                deploymentKind = DeploymentKind.SELF_HOSTED,
                displayName = "Alex's journal server",
            ),
        version = "1.4.0",
    )
private val moveRecord =
    ServerMoveRecord(
        from = moveSource,
        to = MoveEndpoint(moveDestination.origin, moveDestination.descriptor),
        phase = ServerMoveRecord.Phase.UPLOADING,
        uploadTotal = 368,
    )

@Composable
private fun MoveStep(state: MoveServerUiState) {
    ScreenshotTheme {
        MoveServerContent(
            state = state,
            onClose = {},
            onAddressChange = {},
            onCheckServer = {},
            onBack = {},
            onContinueToAccount = {},
            onCreateAccount = {},
            onSignInInstead = {},
            onDeleteSource = {},
            onSignInToSourceAndDelete = {},
            onKeepSource = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Choose() =
    MoveStep(
        MoveServerUiState.ChooseServer(
            source = moveSource,
            address = "journal.example",
            problem = ChooseProblem.Server(ServerProblem.UNREACHABLE),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Review() =
    MoveStep(
        MoveServerUiState.Review(
            source = moveSource,
            destination = moveDestination,
            survey = MoveSurvey(entries = 312, journals = 4, media = 48, drafts = 3, remoteOnlyMedia = 0),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_CreateAccount() =
    MoveStep(
        MoveServerUiState.CreateAccount(
            source = moveSource,
            destination = moveDestination,
            survey = MoveSurvey(entries = 312, journals = 4, media = 48, drafts = 3, remoteOnlyMedia = 0),
            username = "alex",
            problem = AccountProblem.USERNAME_TAKEN,
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Uploading() =
    MoveStep(
        MoveServerUiState.Uploading(
            record = moveRecord,
            progress = MoveProgress(remaining = 120, failed = 0, isSyncing = true, syncedSinceSwitch = false),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Finished() = MoveStep(MoveServerUiState.Finished(record = moveRecord, cleanup = Cleanup.Offered))

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Hosting_WithMove() {
    ScreenshotTheme {
        HostingContent(
            state =
                HostingUiState(
                    server =
                        ConnectedServerInfo(
                            origin = "https://cloud.logdate.app",
                            displayName = "LogDate Cloud",
                            isLogDateCloud = true,
                            publishesIdentityChanges = false,
                        ),
                    health = ServerHealth.Reachable("1.4.0"),
                    canMoveAccount = true,
                ),
            onBack = {},
            onCheckAgain = {},
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
            onSetCaptureMode = { _: LocationCaptureMode -> },
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
fun ClearDataConfirmation_Dialog() {
    ScreenshotTheme {
        ClearDataConfirmationDialog(
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
                onNavigateToReset = {},
                onNavigateToLocation = {},
                onNavigateToPrivacy = {},
                onNavigateToMemories = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = sampleUserProfile,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AccountContent(
                    state =
                        AccountUiState.SignedIn(
                            header = AccountHeader(displayName = "Alex Rivera", username = "alex"),
                            signIn = SignInSummary.Known(passkeyCount = 2, linkedProviders = listOf(LinkedSignInProvider.Kind.GOOGLE)),
                            hasRecoveryPhrase = true,
                            email = EmailRow(address = "alex@example.com", isVerified = true, canVerify = false),
                            server = ServerRow(name = "LogDate Cloud", host = "cloud.logdate.app", isLogDateCloud = true),
                            isSigningOut = false,
                        ),
                    destinations = AccountDestinations({}, {}, {}, {}, {}, {}, {}, {}),
                    onOpenEmailVerification = {},
                    onSignOut = {},
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
                onNavigateToReset = {},
                onNavigateToLocation = {},
                onNavigateToPrivacy = {},
                onNavigateToMemories = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = sampleUserProfile,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AccountContent(
                    state =
                        AccountUiState.SignedIn(
                            header = AccountHeader(displayName = "Alex Rivera", username = "alex"),
                            signIn = SignInSummary.Known(passkeyCount = 2, linkedProviders = listOf(LinkedSignInProvider.Kind.GOOGLE)),
                            hasRecoveryPhrase = true,
                            email = EmailRow(address = "alex@example.com", isVerified = true, canVerify = false),
                            server = ServerRow(name = "LogDate Cloud", host = "cloud.logdate.app", isLogDateCloud = true),
                            isSigningOut = false,
                        ),
                    destinations = AccountDestinations({}, {}, {}, {}, {}, {}, {}, {}),
                    onOpenEmailVerification = {},
                    onSignOut = {},
                )
            }
        }
    }
}
