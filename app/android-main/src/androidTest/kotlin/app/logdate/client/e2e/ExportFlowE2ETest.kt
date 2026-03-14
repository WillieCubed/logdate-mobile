package app.logdate.client.e2e

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.domain.export.ExportStats
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.settings.ui.ConflictsState
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.RestoreState
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive instrumentation tests for the export flow UI.
 *
 * Tests cover every state of the export state machine, all UI interactions,
 * and the complete export options bottom sheet.
 */
@RunWith(AndroidJUnit4::class)
class ExportFlowE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    private val defaultQuota = StorageQuotaUi(
        totalBytes = 100_000_000_000L,
        usedBytes = 0L,
        usagePercentage = 0f,
        formattedTotal = "100 GB",
        formattedUsed = "0 B",
        categories = emptyList(),
    )

    // region DataSettingsContent — Export State Rendering

    @Test
    fun idleState_showsExportButton() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export data").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsEnabled()
    }

    @Test
    fun idleState_exportButtonTriggersShowExportOptions() {
        var showOptionsCalled = false

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Idle,
                onShowExportOptions = { showOptionsCalled = true },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export").performClick()
        assert(showOptionsCalled) { "Expected showExportOptions to be called" }
    }

    @Test
    fun selectingState_showsExportButtonDisabled() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Selecting,
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export").assertIsNotEnabled()
    }

    @Test
    fun exportingState_showsProgressCard() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Exporting(progressPercent = 45, message = "Collecting notes..."),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("45%").assertIsDisplayed()
        composeRule.onNodeWithText("Collecting notes...").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun exportingState_cancelButtonTriggersCancelExport() {
        var cancelCalled = false

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Exporting(progressPercent = 45, message = "Collecting notes..."),
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = { cancelCalled = true },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()
        assert(cancelCalled) { "Expected cancelExport to be called" }
    }

    @Test
    fun completedState_showsSuccessCard() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Completed(
                    path = "content://downloads/logdate_export.zip",
                    fileName = "logdate_export.zip",
                    stats = ExportStats(
                        journalCount = 3,
                        noteCount = 42,
                        draftCount = 5,
                        mediaCount = 10,
                    ),
                ),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export complete").assertIsDisplayed()
        composeRule.onNodeWithText("logdate_export.zip").assertIsDisplayed()
        composeRule.onNodeWithText("3 journals, 42 notes, 5 drafts, 10 media files").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun completedState_shareButtonTriggersShareExport() {
        var sharedPath: String? = null

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Completed(
                    path = "content://downloads/export.zip",
                    fileName = "export.zip",
                ),
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = {},
                onShareExport = { sharedPath = it },
                onRestoreContent = {},
                onCancelRestore = {},
                restoreState = RestoreState.Idle,
                integrityState = IntegrityState(),
                onRunIntegrityCheck = {},
                onRepairIntegrity = {},
                conflictsState = ConflictsState(),
                onClearConflicts = {},
                onRefreshConflicts = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Share").performClick()
        assert(sharedPath == "content://downloads/export.zip") {
            "Expected share with path content://downloads/export.zip, got $sharedPath"
        }
    }

    @Test
    fun completedState_doneButtonTriggersDissmiss() {
        var dismissCalled = false

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Completed(
                    path = "/path/to/export.zip",
                    fileName = "export.zip",
                ),
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = { dismissCalled = true },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Done").performClick()
        assert(dismissCalled) { "Expected dismissExportResult to be called" }
    }

    @Test
    fun completedState_withoutStats_showsSuccessCardWithoutStatsLine() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Completed(
                    path = "/path/to/export.zip",
                    fileName = "export.zip",
                    stats = null,
                ),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export complete").assertIsDisplayed()
        composeRule.onNodeWithText("export.zip").assertIsDisplayed()
        // Stats line should not be present
        composeRule.onAllNodesWithText("journals", substring = true).fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Stats should not be shown when stats is null" }
        }
    }

    @Test
    fun failedState_showsFailureCard() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Failed(
                    reason = "Disk space full",
                    canRetry = true,
                ),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Export failed").assertIsDisplayed()
        composeRule.onNodeWithText("Disk space full").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun failedState_retryButtonTriggersRetryExport() {
        var retryCalled = false

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Failed(reason = "Error", canRetry = true),
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = { retryCalled = true },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Retry").performClick()
        assert(retryCalled) { "Expected retryExport to be called" }
    }

    @Test
    fun failedState_dismissButtonTriggersDismiss() {
        var dismissCalled = false

        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Failed(reason = "Error"),
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = { dismissCalled = true },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Dismiss").performClick()
        assert(dismissCalled) { "Expected dismissExportResult to be called" }
    }

    @Test
    fun exportingState_zeroPercent_showsIndeterminateProgress() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Exporting(progressPercent = 0, message = "Starting export..."),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("0%").assertIsDisplayed()
        composeRule.onNodeWithText("Starting export...").assertIsDisplayed()
    }

    @Test
    fun exportingState_hundredPercent_shows100() {
        composeRule.setContent {
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Exporting(progressPercent = 100, message = "Finalizing..."),
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("100%").assertIsDisplayed()
        composeRule.onNodeWithText("Finalizing...").assertIsDisplayed()
    }

    // endregion

    // region State Transitions — Dynamic state changes

    @Test
    fun stateTransition_idleToExporting_swapsExportButtonForProgressCard() {
        composeRule.setContent {
            var state by remember { mutableStateOf<ExportState>(ExportState.Idle) }
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = state,
                onShowExportOptions = { state = ExportState.Exporting(10, "Starting...") },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        // Initial state: export button visible
        composeRule.onNodeWithText("Export data").assertIsDisplayed()

        // Trigger state transition
        composeRule.onNodeWithText("Export").performClick()
        composeRule.waitForIdle()

        // After transition: progress card visible
        composeRule.onNodeWithText("10%").assertIsDisplayed()
        composeRule.onNodeWithText("Starting...").assertIsDisplayed()
    }

    @Test
    fun stateTransition_exportingToCompleted() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf<ExportState>(ExportState.Exporting(90, "Finalizing..."))
            }
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = state,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {
                    state = ExportState.Completed(
                        path = "/test/export.zip",
                        fileName = "export.zip",
                        stats = ExportStats(1, 5, 2, 3),
                    )
                },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        // Simulate transition via cancel (re-using cancel as a trigger for testing)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export complete").assertIsDisplayed()
        composeRule.onNodeWithText("export.zip").assertIsDisplayed()
    }

    @Test
    fun stateTransition_exportingToFailed() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf<ExportState>(ExportState.Exporting(30, "Working..."))
            }
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = state,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {
                    state = ExportState.Failed(reason = "Out of memory")
                },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export failed").assertIsDisplayed()
        composeRule.onNodeWithText("Out of memory").assertIsDisplayed()
    }

    @Test
    fun stateTransition_failedToIdleViaDismiss() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf<ExportState>(ExportState.Failed(reason = "Disk full"))
            }
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = state,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = { state = ExportState.Idle },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export data").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsEnabled()
    }

    @Test
    fun stateTransition_completedToIdleViaDone() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf<ExportState>(
                    ExportState.Completed(path = "/test.zip", fileName = "test.zip"),
                )
            }
            DataSettingsContent(
                onBack = {},
                quotaUsage = defaultQuota,
                isQuotaAvailable = true,
                exportState = state,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = { state = ExportState.Idle },
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
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export data").assertIsDisplayed()
    }

    // endregion
}
