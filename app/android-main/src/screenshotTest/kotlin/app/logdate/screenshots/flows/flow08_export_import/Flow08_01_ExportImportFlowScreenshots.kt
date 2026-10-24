@file:Suppress("ktlint:standard:filename", "ktlint:standard:function-naming", "ktlint:standard:package-name")

package app.logdate.screenshots.flows.flow08_export_import

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.client.text.toUiText
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.RestoreError
import app.logdate.feature.core.restore.RestoreStage
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.ExportSettingsContent
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.UserProfile
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTestData.COMPACT_PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
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
@Preview(name = "Compact Phone", showBackground = true, device = COMPACT_PHONE)
@Preview(name = "Phone, 150% text", showBackground = true, device = PHONE, fontScale = 1.5f)
@Preview(name = "Phone, 200% text", showBackground = true, device = PHONE, fontScale = 2f)
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
                    message = "Exporting media files\u2026".toUiText(),
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
                    reason = "Not enough storage space to create the export archive.".toUiText(),
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Preview(name = "Phone, 200% text", showBackground = true, device = PHONE, fontScale = 2f)
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

@PreviewTest
@ScreenshotPreviewMatrix
@Preview(name = "Phone, 200% text", showBackground = true, device = PHONE, fontScale = 2f)
@Composable
fun S11_ImportPartialLongFilename() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState =
                RestoreState.Completed(
                    summary =
                        RestoreSummary(
                            source = "logdate-export-2026-10-23-with-a-deliberately-long-filename.zip",
                            journalsImported = 5,
                            notesImported = 42,
                            draftsImported = 3,
                            journalLinksImported = 11,
                            mediaImported = 16,
                            unavailableOriginals = 2,
                            unavailablePreviews = 1,
                            recoveryId = "recovery-2026-10-23",
                        ),
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S12_ImportPaused() {
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
                            recoveryId = "recovery-2026-10-23",
                            metadataIncomplete = true,
                            canResume = true,
                        ),
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S13_ImportRecoveringMedia() {
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
                            recoveryId = "recovery-2026-10-23",
                            metadataIncomplete = true,
                            recoveringMedia = true,
                            mediaRecoveryChecked = 4,
                            mediaRecoveryTotal = 12,
                        ),
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Preview(name = "Phone, 200% text", showBackground = true, device = PHONE, fontScale = 2f)
@Composable
fun S14_ImportPreview() {
    ScreenshotTheme {
        ExportImportPreviewContent(
            restoreState =
                RestoreState.Previewing(
                    preview =
                        ArchivePreview(
                            version = ExportSchemaVersion.V1_2,
                            exportDate = ScreenshotTestData.baseInstant,
                            appVersion = "0.1.0",
                            stats =
                                ExportStats(
                                    journalCount = 5,
                                    noteCount = 42,
                                    draftCount = 3,
                                    mediaCount = 18,
                                ),
                        ),
                    fileName = "logdate-export-2026-10-23-with-a-deliberately-long-filename.zip",
                    options = ImportOptions(),
                ),
        )
    }
}
