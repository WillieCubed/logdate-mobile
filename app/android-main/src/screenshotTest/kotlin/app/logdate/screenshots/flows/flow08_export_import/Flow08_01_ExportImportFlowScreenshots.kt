package app.logdate.screenshots.flows.flow08_export_import

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportStats
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreError
import app.logdate.feature.core.restore.RestoreStage
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.ExportSettingsContent
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

@Composable
private fun ExportImportPreviewContent(
    exportState: ExportState = ExportState.Idle,
    restoreState: RestoreState = RestoreState.Idle,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    ExportSettingsContent(
        onBack = {},
        exportState = exportState,
        onShowExportOptions = {},
        onUpdateExportOptions = {},
        onConfirmExport = {},
        onCancelExport = {},
        onRetryExport = {},
        onDismissExport = {},
        onBrowseExport = {},
        restoreState = restoreState,
        onShowRestoreSheet = {},
        onSelectRestoreFile = {},
        onUpdateImportOptions = {},
        onConfirmImport = {},
        onCancelRestore = {},
        onRetryRestore = {},
        onDismissRestore = {},
        snackbarHostState = snackbarHostState,
    )
}

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
@ScreenshotPreviewMatrix
@Composable
fun S02_ExportSettingsIdle() {
    ScreenshotTheme {
        ExportImportPreviewContent()
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_ExportConfiguring() {
    ScreenshotTheme {
        ExportImportPreviewContent(
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
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_ExportInProgress() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            exportState =
                ExportState.Exporting(
                    progressPercent = 65,
                    message = "Exporting media files\u2026",
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_ExportCompleted() {
    ScreenshotTheme {
        ExportImportPreviewContent(
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
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_ExportFailed() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            exportState =
                ExportState.Failed(
                    reason = "Not enough storage space to create the export archive.",
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_ImportConfirming() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState = RestoreState.Confirming,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_ImportInProgress() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState =
                RestoreState.Restoring(
                    stage = RestoreStage.RESTORING_NOTES,
                    progressPercent = 45,
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_ImportCompleted() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState =
                RestoreState.Completed(
                    summary =
                        RestoreSummary(
                            source = "logdate-export.zip",
                            journalsImported = 5,
                            notesImported = 42,
                            draftsImported = 3,
                            journalLinksImported = 11,
                            mediaImported = 18,
                            warnings = listOf("2 attachments were skipped because they were already present."),
                        ),
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_ImportFailed() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState = RestoreState.Failed(error = RestoreError.INVALID_ARCHIVE),
        )
    }
}
