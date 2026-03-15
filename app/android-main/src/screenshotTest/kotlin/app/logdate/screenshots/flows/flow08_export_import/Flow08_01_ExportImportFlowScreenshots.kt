package app.logdate.screenshots.flows.flow08_export_import

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportStats
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.ExportSettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private val sampleUserProfile =
    UserProfile(
        name = "Alex Johnson",
        username = "alex_j",
        isEditable = true,
        isAuthenticated = true,
    )

// ─── Export Flow: Settings → Export Settings → Configure → In Progress → Done ───

/** Step 1: User is on the settings overview and taps "Export & Import". */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_SettingsOverview() {
    ScreenshotTheme {
        SettingsOverviewContent(
            onBack = {},
            onNavigateToProfile = {},
            onNavigateToAccount = {},
            onNavigateToDevices = {},
            onNavigateToDangerZone = {},
            onNavigateToLocation = {},
            onNavigateToPrivacy = {},
            onNavigateToMemories = {},
            onNavigateToSync = {},
            onNavigateToExport = {},
            userProfile = sampleUserProfile,
        )
    }
}

/** Step 2: User lands on the Export & Import settings screen. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_ExportSettingsIdle() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
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
            onConfirmRestore = {},
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

/** Step 3: User taps "Export" — the export options bottom sheet appears. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_ExportConfiguring() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState =
                ExportState.Configuring(
                    counts =
                        ExportCounts(
                            journalCount = 5,
                            noteCount = 42,
                            draftCount = 3,
                            mediaCount = 18,
                        ),
                ),
            isExportSheetVisible = true,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Idle,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Step 4: Export is in progress with a determinate progress bar. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_ExportInProgress() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState =
                ExportState.Exporting(
                    progressPercent = 65,
                    message = "Exporting media files\u2026",
                ),
            isExportSheetVisible = true,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Idle,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Step 5: Export completes successfully with stats. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_ExportCompleted() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState =
                ExportState.Completed(
                    path = "/storage/emulated/0/Download/logdate-export.zip",
                    fileName = "logdate-export.zip",
                    stats =
                        ExportStats(
                            journalCount = 5,
                            noteCount = 42,
                            draftCount = 3,
                            mediaCount = 18,
                        ),
                ),
            isExportSheetVisible = true,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Idle,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Alternate ending: Export fails with an error message. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_ExportFailed() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState =
                ExportState.Failed(
                    reason = "Not enough storage space to create the export archive.",
                ),
            isExportSheetVisible = true,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Idle,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

// ─── Import Flow: Export Settings → Confirm → In Progress → Done ─────────────────

/** Step 7: User taps "Import" — the import confirmation bottom sheet appears. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_ImportConfirming() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState = ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Confirming,
            isRestoreSheetVisible = true,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Step 8: Import is in progress with an indeterminate progress indicator. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_ImportInProgress() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState = ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState = RestoreState.Restoring,
            isRestoreSheetVisible = true,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Step 9: Import completes successfully with stats. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_ImportCompleted() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState = ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState =
                RestoreState.Completed(
                    summary =
                        RestoreSummary(
                            source = "logdate-export.zip",
                            journalsImported = 5,
                            notesImported = 42,
                            draftsImported = 3,
                            journalLinksImported = 28,
                            mediaImported = 18,
                        ),
                ),
            isRestoreSheetVisible = true,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Step 10: Import completes with warnings about skipped entries. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_ImportCompletedWithWarnings() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState = ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState =
                RestoreState.Completed(
                    summary =
                        RestoreSummary(
                            source = "logdate-export.zip",
                            journalsImported = 5,
                            notesImported = 38,
                            draftsImported = 3,
                            journalLinksImported = 24,
                            mediaImported = 15,
                            warnings =
                                listOf(
                                    "Skipped 4 notes with invalid UUIDs",
                                    "3 media files could not be found in archive",
                                ),
                        ),
                ),
            isRestoreSheetVisible = true,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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

/** Alternate ending: Import fails with an error message. */
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S11_ImportFailed() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ExportSettingsContent(
            onBack = {},
            exportState = ExportState.Idle,
            onShowExportOptions = {},
            onUpdateExportOptions = {},
            onConfirmExport = {},
            onCancelExport = {},
            onRetryExport = {},
            onDismissExport = {},
            onBrowseExport = {},
            restoreState =
                RestoreState.Failed(
                    reason = "The archive appears to be corrupted or is not a valid LogDate export.",
                ),
            isRestoreSheetVisible = true,
            onShowRestoreSheet = {},
            onConfirmRestore = {},
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
